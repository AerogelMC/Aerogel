package dev.aerogel.loader.context;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lock-free MPSC, single-consumer publication lane owned by one world.
 *
 * <p>The queue's linearization order is the world's commit sequence. A private
 * marker bounds each drain generation, so producers cannot extend a running
 * drain indefinitely. There is no server-thread join and no relationship
 * between lanes belonging to different dimensions.</p>
 */
final class WorldCommitLane implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Aerogel-World-Commits");
    private static final CommitBatch GENERATION_END = new CommitBatch(new Runnable[0]);

    private final ContextServiceImpl scheduler;
    private final ConcurrentLinkedQueue<CommitBatch> queue = new ConcurrentLinkedQueue<>();
    private final PaddedAtomicBoolean scheduled = new PaddedAtomicBoolean();
    private volatile boolean closing;

    WorldCommitLane(ContextServiceImpl scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    void offer(Runnable[] commits) {
        Objects.requireNonNull(commits, "commits");
        if (commits.length == 0) return;
        queue.add(new CommitBatch(commits));
        schedule();
    }

    CompletableFuture<Void> submit(Runnable action) {
        Objects.requireNonNull(action, "action");
        CompletableFuture<Void> completion = new CompletableFuture<>();
        offer(new Runnable[] { () -> {
            try {
                action.run();
                completion.complete(null);
            } catch (Throwable error) {
                completion.completeExceptionally(error);
            }
        } });
        return completion;
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        // A native owner publishes its world commits before releasing its own
        // shutdown permit. Claim this generation first, so shutdown observes a
        // continuous unfinished-work chain through the final world mutation.
        NativeTickCoordinator.beginAsynchronousWork();
        if (!scheduler.dispatchWorldCommit(this::drainGeneration)) {
            if (closing) {
                // An already-running Context may publish after pool shutdown has
                // begun. It becomes the world's sole consumer instead of losing
                // the accepted commit or falling back to the server thread.
                drainGeneration();
                return;
            }
            scheduled.set(false);
            NativeTickCoordinator.endAsynchronousWork();
            throw new IllegalStateException("Context scheduler rejected a world commit");
        }
    }

    private void drainGeneration() {
        try {
            do {
                queue.add(GENERATION_END);
                CommitBatch batch;
                while ((batch = queue.poll()) != null && batch != GENERATION_END) {
                    batch.run();
                }
                // Normal operation is generation-bounded. Shutdown is the only
                // time this owner drains all already accepted work in one pass.
            } while (closing && !queue.isEmpty());
        } finally {
            scheduled.set(false);
            try {
                // Claim a following generation before releasing this one. This
                // prevents a transient zero in the shutdown counter while work
                // accepted by this world is still queued.
                if (!queue.isEmpty()) schedule();
            } finally {
                NativeTickCoordinator.endAsynchronousWork();
            }
        }
    }

    @Override
    public void close() {
        closing = true;
        if (!queue.isEmpty() && !scheduled.get()) schedule();
    }

    private record CommitBatch(Runnable[] commits) {
        private void run() {
            for (Runnable commit : commits) {
                try {
                    commit.run();
                } catch (Throwable error) {
                    LOGGER.log(Level.SEVERE, "World-scoped commit failed", error);
                }
            }
        }
    }
}
