package dev.aerogel.loader.network;

import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

/** CPU workers shared by independently serialized per-connection compression lanes. */
public final class CompressionWorkers {
    private static final Object LIFECYCLE = new Object();
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final Executor EXECUTOR = task ->
        execute(PacketPriority.INTERACTIVE, task);
    private static volatile WorkerState workers;

    private CompressionWorkers() {
    }

    public static Executor executor() {
        return EXECUTOR;
    }

    public static void execute(PacketPriority priority, Runnable task) {
        state().execute(priority, task);
    }

    private static WorkerState state() {
        WorkerState current = workers;
        if (current != null) return current;
        synchronized (LIFECYCLE) {
            current = workers;
            if (current == null) {
                int workerCount = configuredWorkerCount();
                if (workerCount < 1) {
                    throw new IllegalArgumentException(
                        "Compression worker count must be positive");
                }
                current = new WorkerState(workerCount);
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
        WorkerState current;
        synchronized (LIFECYCLE) {
            current = workers;
            workers = null;
        }
        if (current != null) current.pool.shutdown();
    }

    /**
     * A ForkJoin submission is only a worker permit. Work is distributed across
     * per-worker stripes; an available worker scans interactive stripes before
     * bulk stripes, without introducing one globally contended priority queue.
     */
    private static final class WorkerState {
        private final ConcurrentLinkedQueue<Runnable>[] interactive;
        private final ConcurrentLinkedQueue<Runnable>[] bulk;
        private final ForkJoinPool pool;
        private final Runnable drainOne = this::drainOne;

        @SuppressWarnings({"unchecked", "rawtypes"})
        private WorkerState(int workerCount) {
            interactive = new ConcurrentLinkedQueue[workerCount];
            bulk = new ConcurrentLinkedQueue[workerCount];
            for (int index = 0; index < workerCount; index++) {
                interactive[index] = new ConcurrentLinkedQueue<>();
                bulk[index] = new ConcurrentLinkedQueue<>();
            }
            ForkJoinPool.ForkJoinWorkerThreadFactory factory = owner -> {
                ForkJoinWorkerThread worker =
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(owner);
                worker.setName(
                    "Aerogel-Compression-" + WORKER_IDS.incrementAndGet());
                worker.setDaemon(true);
                return worker;
            };
            pool = new ForkJoinPool(
                workerCount,
                factory,
                (thread, error) -> error.printStackTrace(System.err),
                true
            );
        }

        private void execute(PacketPriority priority, Runnable task) {
            int stripe = stripe(Thread.currentThread(), interactive.length);
            ConcurrentLinkedQueue<Runnable> target = priority == PacketPriority.BULK
                ? bulk[stripe] : interactive[stripe];
            target.add(task);
            try {
                pool.execute(drainOne);
            } catch (Throwable error) {
                target.remove(task);
                throw error;
            }
        }

        private void drainOne() {
            int start = stripe(Thread.currentThread(), interactive.length);
            Runnable task = poll(interactive, start);
            if (task == null) task = poll(bulk, start);
            if (task != null) task.run();
        }

        private static Runnable poll(
            ConcurrentLinkedQueue<Runnable>[] stripes, int start
        ) {
            int index = start;
            do {
                Runnable task = stripes[index].poll();
                if (task != null) return task;
                if (++index == stripes.length) index = 0;
            } while (index != start);
            return null;
        }

        private static int stripe(Thread thread, int stripeCount) {
            if (thread instanceof ForkJoinWorkerThread worker) {
                return worker.getPoolIndex() % stripeCount;
            }
            int hash = Long.hashCode(thread.threadId());
            hash ^= hash >>> 16;
            return (hash & Integer.MAX_VALUE) % stripeCount;
        }
    }
}
