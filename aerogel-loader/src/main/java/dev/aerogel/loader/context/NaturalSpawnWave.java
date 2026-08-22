package dev.aerogel.loader.context;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Preserves vanilla's cross-tick natural-spawn ordering without blocking the
 * server thread. Chunks inside one wave remain independent Context work; only
 * the next wave's entity-count snapshot waits for this wave to finish.
 */
public final class NaturalSpawnWave {
    private static final int CLOSED = -1;

    private final CompletableFuture<Void> predecessor;
    private final CompletableFuture<Void> completion;
    /* One hold for state preparation and one for synchronous task registration. */
    private final PaddedAtomicInteger pending = new PaddedAtomicInteger(2);
    private final PaddedAtomicBoolean sealed = new PaddedAtomicBoolean();

    NaturalSpawnWave(
        CompletableFuture<Void> predecessor, CompletableFuture<Void> completion
    ) {
        this.predecessor = Objects.requireNonNull(predecessor, "predecessor");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    void afterPredecessor(Runnable action) {
        Objects.requireNonNull(action, "action");
        predecessor.whenComplete((ignored, failure) -> action.run());
    }

    CompletableFuture<Void> completion() {
        return completion;
    }

    /** Registers asynchronous work before its parent registration is released. */
    public boolean register() {
        int current = pending.get();
        while (current != CLOSED) {
            if (pending.compareAndSet(current, current + 1)) return true;
            current = pending.get();
        }
        return false;
    }

    public void taskComplete() {
        release();
    }

    void preparationComplete() {
        release();
    }

    public void seal() {
        if (sealed.compareAndSet(false, true)) release();
    }

    private void release() {
        int current = pending.get();
        while (current > 0) {
            int updated = current - 1;
            if (!pending.compareAndSet(current, updated)) {
                current = pending.get();
                continue;
            }
            if (updated == 0 && pending.compareAndSet(0, CLOSED)) {
                completion.complete(null);
            }
            return;
        }
    }
}
