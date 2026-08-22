package dev.aerogel.loader.context;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates the server-thread commit boundary around native context work. */
public final class NativeTickCoordinator {
    private static final ThreadLocal<NativeFrame> NATIVE_WORK = new ThreadLocal<>();
    private static final ConcurrentLinkedQueue<Runnable> GLOBAL_COMMITS =
        new ConcurrentLinkedQueue<>();
    private static final AtomicInteger OUTSTANDING = new AtomicInteger();
    private static final PaddedAtomicLong SERVER_TICK = new PaddedAtomicLong();

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
        if (NATIVE_WORK.get() != null) {
            throw new IllegalStateException("Nested native context work");
        }
        NativeFrame frame = new NativeFrame();
        NATIVE_WORK.set(frame);
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
            } finally {
                NATIVE_WORK.remove();
            }
            Runnable completion = () -> {
                try {
                    committed.run();
                } finally {
                    OUTSTANDING.decrementAndGet();
                }
            };
            if (frame.commits.isEmpty()) {
                for (Runnable published : frame.afterGlobalCommits) published.run();
                completion.run();
            } else {
                GLOBAL_COMMITS.add(() -> {
                    try {
                        for (Runnable commit : frame.commits) commit.run();
                    } finally {
                        try {
                            for (Runnable published : frame.afterGlobalCommits) {
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
        return Thread.currentThread() instanceof ForkJoinWorkerThread
            && NATIVE_WORK.get() != null;
    }

    public static void beginServerTick() {
        SERVER_TICK.incrementAndGet();
    }

    static long currentServerTick() {
        return SERVER_TICK.get();
    }

    public static boolean deferGlobalCommit(Runnable commit) {
        NativeFrame frame = NATIVE_WORK.get();
        if (frame == null) return false;
        frame.commits.add(commit);
        return true;
    }

    /** Runs after every global publication produced by the current native task. */
    static boolean afterGlobalCommit(Runnable completion) {
        NativeFrame frame = NATIVE_WORK.get();
        if (frame == null) return false;
        frame.afterGlobalCommits.add(completion);
        return true;
    }

    /** Adds owner-local work to the end of the current native transaction. */
    public static boolean deferNativeCompletion(Runnable completion) {
        NativeFrame frame = NATIVE_WORK.get();
        if (frame == null) return false;
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
        if (frame == null) return null;
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

    private static final class NativeFrame {
        private final List<Runnable> commits = new ArrayList<>();
        private final List<Runnable> afterGlobalCommits = new ArrayList<>();
        private final List<Runnable> nativeCompletions = new ArrayList<>();
        private final Map<Object, Object> attachments = new IdentityHashMap<>();
    }
}
