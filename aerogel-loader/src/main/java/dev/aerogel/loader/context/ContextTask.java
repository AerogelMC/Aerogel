package dev.aerogel.loader.context;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

record ContextTask(
    long epoch,
    long[] scopeKeys,
    Runnable action,
    CompletableFuture<Void> completion,
    Runnable rejection,
    AtomicReference<NeighborhoodLease> neighborhoodLease,
    NativePhase phase
) {
    ContextTask(
        long epoch,
        long[] scopeKeys,
        Runnable action,
        CompletableFuture<Void> completion,
        Runnable rejection
    ) {
        this(epoch, scopeKeys, action, completion, rejection,
            new AtomicReference<>(), NativePhase.DEFAULT);
    }

    ContextTask(
        long epoch,
        long[] scopeKeys,
        Runnable action,
        CompletableFuture<Void> completion,
        Runnable rejection,
        NativePhase phase
    ) {
        this(epoch, scopeKeys, action, completion, rejection,
            new AtomicReference<>(), phase);
    }

    NeighborhoodLease neighborhoodLease(ChunkContextImpl primary, ContextServiceImpl scheduler) {
        NeighborhoodLease current = neighborhoodLease.get();
        if (current != null) return current;
        NeighborhoodLease created = scheduler.newLease(primary);
        return neighborhoodLease.compareAndSet(null, created)
            ? created
            : neighborhoodLease.get();
    }
}
