package dev.aerogel.loader.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Separates chunk-owned preparation from server-owned state publication.
 *
 * <p>A Context executes the preparation immediately and records only the exact
 * server-owned mutations it produced. Those mutations are then published as one
 * ordered server commit. The returned future settles after that publication, so
 * a later distance generation cannot overtake it. No lock, timer, or size-based
 * batching policy is involved.</p>
 */
public final class OwnerPublicationBarrier {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private OwnerPublicationBarrier() { }

    public static CompletableFuture<Void> run(Runnable preparation) {
        return run(preparation, NativeTickCoordinator::submitGlobalCommit);
    }

    public static CompletableFuture<Void> run(
        Runnable preparation, Consumer<Runnable> publicationExecutor
    ) {
        Objects.requireNonNull(preparation, "preparation");
        Objects.requireNonNull(publicationExecutor, "publicationExecutor");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested owner publication barrier");
        }

        Frame frame = new Frame();
        CURRENT.set(frame);
        Throwable preparationFailure = null;
        try {
            preparation.run();
        } catch (Throwable error) {
            preparationFailure = error;
        } finally {
            CURRENT.remove();
        }

        CompletableFuture<Void> settled = new CompletableFuture<>();
        Throwable capturedFailure = preparationFailure;
        if (frame.commits.isEmpty()) {
            settle(settled, capturedFailure);
            return settled;
        }

        Runnable[] commits = frame.commits.toArray(Runnable[]::new);
        publicationExecutor.accept(() -> {
            Throwable failure = capturedFailure;
            for (Runnable commit : commits) {
                try {
                    commit.run();
                } catch (Throwable error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            settle(settled, failure);
        });
        return settled;
    }

    /** Records a server-owned mutation in the current owner transaction. */
    public static boolean defer(Runnable commit) {
        Objects.requireNonNull(commit, "commit");
        Frame frame = CURRENT.get();
        if (frame == null) return false;
        frame.commits.add(commit);
        return true;
    }

    private static void settle(CompletableFuture<Void> future, Throwable failure) {
        if (failure == null) future.complete(null);
        else future.completeExceptionally(failure);
    }

    private static final class Frame {
        private final List<Runnable> commits = new ArrayList<>();
    }
}
