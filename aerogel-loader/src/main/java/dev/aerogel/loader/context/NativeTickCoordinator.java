package dev.aerogel.loader.context;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Coordinates the server-thread commit boundary around native context work. */
public final class NativeTickCoordinator {
    private static final ThreadLocal<NativeFrame> NATIVE_WORK = new ThreadLocal<>();
    private static final ConcurrentLinkedQueue<Runnable> GLOBAL_COMMITS =
        new ConcurrentLinkedQueue<>();
    private static final AtomicInteger OUTSTANDING = new AtomicInteger();

    private NativeTickCoordinator() { }

    static <T> void runNative(List<T> items, Consumer<T> action, Runnable committed) {
        if (NATIVE_WORK.get() != null) {
            throw new IllegalStateException("Nested native context work");
        }
        NativeFrame frame = new NativeFrame();
        NATIVE_WORK.set(frame);
        try {
            for (T item : items) action.accept(item);
        } finally {
            NATIVE_WORK.remove();
            Runnable completion = () -> {
                try {
                    committed.run();
                } finally {
                    OUTSTANDING.decrementAndGet();
                }
            };
            if (frame.commits.isEmpty()) {
                completion.run();
            } else {
                GLOBAL_COMMITS.add(() -> {
                    for (Runnable commit : frame.commits) commit.run();
                });
                completion.run();
            }
        }
    }

    public static boolean isNativeWorker() {
        return NATIVE_WORK.get() != null;
    }

    public static boolean deferGlobalCommit(Runnable commit) {
        NativeFrame frame = NATIVE_WORK.get();
        if (frame == null) return false;
        frame.commits.add(commit);
        return true;
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
    }
}
