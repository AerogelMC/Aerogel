package dev.aerogel.loader.context;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

record ContextTask(
    long epoch,
    long[] scopeKeys,
    Runnable action,
    CompletableFuture<Void> completion,
    Runnable rejection,
    Runnable unavailableRejection,
    AtomicReference<NeighborhoodLease> neighborhoodLease,
    AtomicReference<ChunkContextImpl> waiterHandoff,
    NativePhase phase,
    NeighborCausalGroup causalGroup
) {
    ContextTask(
        long epoch,
        long[] scopeKeys,
        Runnable action,
        CompletableFuture<Void> completion,
        Runnable rejection
    ) {
        this(epoch, scopeKeys, action, completion, rejection, rejection,
            new AtomicReference<>(), new AtomicReference<>(), NativePhase.DEFAULT, null);
    }

    ContextTask(
        long epoch,
        long[] scopeKeys,
        Runnable action,
        CompletableFuture<Void> completion,
        Runnable rejection,
        NativePhase phase
    ) {
        this(epoch, scopeKeys, action, completion, rejection, rejection,
            new AtomicReference<>(), new AtomicReference<>(), phase, null);
    }

    ContextTask(
        long epoch,
        long[] scopeKeys,
        Runnable action,
        CompletableFuture<Void> completion,
        Runnable rejection,
        Runnable unavailableRejection,
        NativePhase phase
    ) {
        this(epoch, scopeKeys, action, completion, rejection, unavailableRejection,
            new AtomicReference<>(), new AtomicReference<>(), phase, null);
    }

    ContextTask withCausalGroup(NeighborCausalGroup group) {
        return new ContextTask(epoch, scopeKeys, action, completion, rejection,
            unavailableRejection, neighborhoodLease, waiterHandoff, phase, group);
    }

    NeighborhoodLease neighborhoodLease(ChunkContextImpl primary, ContextServiceImpl scheduler) {
        NeighborhoodLease current = neighborhoodLease.get();
        if (current != null) return current;
        NeighborhoodLease created = scheduler.newLease(primary);
        return neighborhoodLease.compareAndSet(null, created)
            ? created
            : neighborhoodLease.get();
    }

    NeighborhoodLease existingNeighborhoodLease() {
        return neighborhoodLease.get();
    }

    boolean claimWaiterHandoff(ChunkContextImpl context) {
        return waiterHandoff.compareAndSet(null, context);
    }

    ChunkContextImpl takeWaiterHandoff() {
        return waiterHandoff.getAndSet(null);
    }
}
