package dev.aerogel.loader.context;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** One exact multi-Context ownership attempt and its single ownership waiter. */
final class NeighborhoodLease {
    private final long id;
    private final ChunkContextImpl primary;
    private final AtomicReference<Runnable> ownershipWaiter = new AtomicReference<>();

    NeighborhoodLease(long id, ChunkContextImpl primary) {
        this.id = id;
        this.primary = Objects.requireNonNull(primary, "primary");
    }

    long id() { return id; }
    ChunkContextImpl primary() { return primary; }

    void park(Runnable continuation) {
        if (!ownershipWaiter.compareAndSet(null,
            Objects.requireNonNull(continuation, "continuation"))) {
            throw new IllegalStateException("Neighborhood lease already has a parked owner");
        }
    }

    void wake() {
        Runnable continuation = ownershipWaiter.getAndSet(null);
        if (continuation != null) continuation.run();
    }

}
