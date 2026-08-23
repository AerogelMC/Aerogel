package dev.aerogel.loader.context;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/** Coordinates the server-thread commit boundary around native context work. */
public final class NativeTickCoordinator {
    private static final ContextWorkerLocal<NativeFrame> NATIVE_WORK =
        ContextWorkerLocal.withInitial(NativeFrame::new);
    private static final ConcurrentLinkedQueue<Runnable> GLOBAL_COMMITS =
        new ConcurrentLinkedQueue<>();
    private static final AtomicInteger OUTSTANDING = new AtomicInteger();
    private static final AtomicBoolean MAIN_WAKE_SCHEDULED = new AtomicBoolean();
    private static volatile MinecraftServer mainServer;
    private static volatile boolean shutdownDraining;
    private static final PaddedAtomicLong SERVER_TICK = new PaddedAtomicLong();
    private static final PaddedAtomicReference<NativeTickToken> CURRENT_TICK =
        new PaddedAtomicReference<>();

    private NativeTickCoordinator() { }

    static <T> void runNative(List<T> items, Consumer<T> action, Runnable committed) {
        runNative(items, action, committed, false);
    }

    /**
     * Runs an owner transaction whose next lane entry depends on publication of any
     * server-owned indexes changed by this entry. Contexts that did not produce a
     * global publication, and every unrelated Context, remain fully independent.
     */
    static <T> void runNativeAfterGlobalCommit(
        List<T> items, Consumer<T> action, Runnable committed
    ) {
        runNative(items, action, committed, true);
    }

    private static <T> void runNative(
        List<T> items,
        Consumer<T> action,
        Runnable committed,
        boolean commitBeforeContinuation
    ) {
        NativeFrame frame = NATIVE_WORK.get();
        if (!frame.enter()) {
            throw new IllegalStateException("Nested native context work");
        }
        Throwable failure = null;
        try {
            for (T item : items) {
                try {
                    action.accept(item);
                } catch (Throwable error) {
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new RuntimeException(failure);
        } finally {
            try {
                for (Runnable completion : frame.nativeCompletions) completion.run();
            } catch (Throwable error) {
                // Match the old one-frame-per-transaction lifetime: a failed native
                // completion must not leak this transaction's publications into the
                // next transaction that happens to use the same worker.
                frame.discard();
                throw error;
            } finally {
                frame.leaveNativePhase();
            }
            FramePublication publication = frame.detachPublication();
            Runnable completion = () -> {
                try {
                    committed.run();
                } finally {
                    OUTSTANDING.decrementAndGet();
                }
            };
            if (publication.commits.length == 0) {
                for (Runnable published : publication.afterGlobalCommits) published.run();
                completion.run();
            } else {
                GLOBAL_COMMITS.add(() -> {
                    try {
                        for (Runnable commit : publication.commits) commit.run();
                    } finally {
                        try {
                            for (Runnable published : publication.afterGlobalCommits) {
                                published.run();
                            }
                        } finally {
                            if (commitBeforeContinuation) completion.run();
                        }
                    }
                });
                if (!commitBeforeContinuation) completion.run();
            }
        }
    }

    public static boolean isNativeWorker() {
        // Native frames are entered only by ContextServiceImpl's ForkJoin pool.
        // The type guard keeps ordinary server-thread block operations completely
        // off the ThreadLocal path while the frame check still rejects unrelated
        // ForkJoin pools exactly.
        return Thread.currentThread() instanceof ContextWorkerThread
            && NATIVE_WORK.get().active;
    }

    public static void beginServerTick() {
        long serverTick = SERVER_TICK.incrementAndGet();
        NativeTickToken token = new NativeTickToken(serverTick);
        NativeTickToken previous = CURRENT_TICK.getAndSet(token);
        if (previous != null) previous.seal();
    }

    public static void registerMainServer(MinecraftServer server) {
        mainServer = server;
        shutdownDraining = false;
    }

    /**
     * Prevents MinecraftServer.execute from becoming a foreign-thread inline
     * executor after vanilla has stopped accepting ordinary server tasks.
     * The server thread explicitly pumps GLOBAL_COMMITS during shutdown.
     */
    public static void beginShutdownDrain() {
        shutdownDraining = true;
    }

    public static void finishShutdownDrain() {
        mainServer = null;
        MAIN_WAKE_SCHEDULED.set(false);
    }

    public static void endServerTick() {
        NativeTickToken token = CURRENT_TICK.getAndSet(null);
        if (token != null) token.seal();
    }

    static long currentServerTick() {
        return SERVER_TICK.get();
    }

    static NativeTickToken currentTickToken() {
        return CURRENT_TICK.get();
    }

    public static boolean deferGlobalCommit(Runnable commit) {
        NativeFrame frame = NATIVE_WORK.get();
        if (!frame.active) return false;
        frame.commits.add(commit);
        return true;
    }

    /** Runs after every global publication produced by the current native task. */
    static boolean afterGlobalCommit(Runnable completion) {
        NativeFrame frame = NATIVE_WORK.get();
        if (!frame.active) return false;
        frame.afterGlobalCommits.add(completion);
        return true;
    }

    /** Adds owner-local work to the end of the current native transaction. */
    public static boolean deferNativeCompletion(Runnable completion) {
        NativeFrame frame = NATIVE_WORK.get();
        if (!frame.active) return false;
        frame.nativeCompletions.add(completion);
        return true;
    }

    /**
     * Returns one attachment per identity key for the current native transaction.
     * Callers use this to coalesce immutable commit data without introducing a
     * process-wide queue or a cross-Context lock.
     */
    @SuppressWarnings("unchecked")
    public static <T> T nativeAttachment(Object key, Supplier<? extends T> factory) {
        NativeFrame frame = NATIVE_WORK.get();
        if (!frame.active) return null;
        Object existing = frame.attachments.get(key);
        if (existing != null) return (T) existing;
        T created = factory.get();
        frame.attachments.put(key, created);
        return created;
    }

    /** Enqueues an asynchronous fallback at the server-thread commit boundary. */
    static void submitMainThread(Runnable action) {
        GLOBAL_COMMITS.add(action);
    }

    /** Publishes a completed immutable result for the server-thread commit phase. */
    public static void submitGlobalCommit(Runnable action) {
        GLOBAL_COMMITS.add(action);
        wakeMainThread();
    }

    static void taskSubmitted() {
        OUTSTANDING.incrementAndGet();
    }

    static void taskRejected() {
        OUTSTANDING.decrementAndGet();
    }

    public static void pumpMainThread() {
        drainGlobalCommits();
    }

    public static void drainForShutdown() {
        while (OUTSTANDING.get() != 0 || !GLOBAL_COMMITS.isEmpty()) {
            pumpMainThread();
            Thread.onSpinWait();
        }
        pumpMainThread();
    }

    private static void drainGlobalCommits() {
        Runnable commit;
        while ((commit = GLOBAL_COMMITS.poll()) != null) commit.run();
    }

    private static void wakeMainThread() {
        MinecraftServer server = mainServer;
        if (server == null || shutdownDraining || server.isSameThread()
            || !MAIN_WAKE_SCHEDULED.compareAndSet(false, true)) return;
        server.execute(() -> {
            MAIN_WAKE_SCHEDULED.set(false);
            drainGlobalCommits();
        });
    }

    private static final class NativeFrame {
        private static final Runnable[] NO_ACTIONS = new Runnable[0];

        private boolean active;
        private final List<Runnable> commits = new ArrayList<>();
        private final List<Runnable> afterGlobalCommits = new ArrayList<>();
        private final List<Runnable> nativeCompletions = new ArrayList<>();
        private final Map<Object, Object> attachments = new IdentityHashMap<>();

        private boolean enter() {
            if (active) return false;
            active = true;
            return true;
        }

        private void leaveNativePhase() {
            active = false;
            nativeCompletions.clear();
            attachments.clear();
        }

        private FramePublication detachPublication() {
            Runnable[] detachedCommits = commits.isEmpty()
                ? NO_ACTIONS : commits.toArray(NO_ACTIONS);
            Runnable[] detachedAfterCommits = afterGlobalCommits.isEmpty()
                ? NO_ACTIONS : afterGlobalCommits.toArray(NO_ACTIONS);
            commits.clear();
            afterGlobalCommits.clear();
            if (detachedCommits.length == 0 && detachedAfterCommits.length == 0) {
                return FramePublication.EMPTY;
            }
            return new FramePublication(detachedCommits, detachedAfterCommits);
        }

        private void discard() {
            commits.clear();
            afterGlobalCommits.clear();
            nativeCompletions.clear();
            attachments.clear();
        }
    }

    private record FramePublication(
        Runnable[] commits, Runnable[] afterGlobalCommits
    ) {
        private static final FramePublication EMPTY =
            new FramePublication(NativeFrame.NO_ACTIONS, NativeFrame.NO_ACTIONS);
    }
}
