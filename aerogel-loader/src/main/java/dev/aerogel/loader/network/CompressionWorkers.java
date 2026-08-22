package dev.aerogel.loader.network;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

/** CPU workers shared by independently serialized per-connection compression lanes. */
public final class CompressionWorkers {
    private static final Object LIFECYCLE = new Object();
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final Executor EXECUTOR = CompressionWorkers::execute;
    private static volatile ForkJoinPool workers;

    private CompressionWorkers() {
    }

    public static Executor executor() {
        return EXECUTOR;
    }

    private static void execute(Runnable task) {
        pool().execute(task);
    }

    private static ForkJoinPool pool() {
        ForkJoinPool current = workers;
        if (current != null) return current;
        synchronized (LIFECYCLE) {
            current = workers;
            if (current == null) {
                int workerCount = configuredWorkerCount();
                if (workerCount < 1) {
                    throw new IllegalArgumentException(
                        "Compression worker count must be positive");
                }
                ForkJoinPool.ForkJoinWorkerThreadFactory factory = pool -> {
                    ForkJoinWorkerThread worker =
                        ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    worker.setName(
                        "Aerogel-Compression-" + WORKER_IDS.incrementAndGet());
                    worker.setDaemon(true);
                    return worker;
                };
                current = new ForkJoinPool(
                    workerCount,
                    factory,
                    (thread, error) -> error.printStackTrace(System.err),
                    true
                );
                workers = current;
            }
        }
        return current;
    }

    public static int configuredWorkerCount() {
        int available = Runtime.getRuntime().availableProcessors();
        return Integer.getInteger(
            "aerogel.network.compression.workers", available);
    }

    public static void shutdown() {
        ForkJoinPool current;
        synchronized (LIFECYCLE) {
            current = workers;
            workers = null;
        }
        if (current != null) current.shutdown();
    }
}
