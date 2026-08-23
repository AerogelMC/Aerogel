package dev.aerogel.loader.network;

import dev.aerogel.loader.context.PaddedAtomicLong;

/**
 * Change-sequence gate for a connection's pre-channel action queue.
 *
 * <p>Publishing and draining stay lock-free here. The queue itself retains
 * vanilla's synchronization for the rare drain that actually has work. A
 * publication racing with a drain advances the sequence and therefore forces
 * a later drain; it cannot be hidden by an older completion.</p>
 */
public final class PendingActionDrainGate {
    public static final long NONE = Long.MIN_VALUE;

    private final PaddedAtomicLong published = new PaddedAtomicLong();
    private final PaddedAtomicLong drained = new PaddedAtomicLong(NONE);

    /** Called after an action has become visible in the concurrent queue. */
    public void published() {
        published.incrementAndGet();
    }

    /** Returns the generation to drain, or {@link #NONE} when already current. */
    public long requiredGeneration() {
        long generation = published.get();
        return drained.get() == generation ? NONE : generation;
    }

    /** Records exactly the generation observed before the completed drain. */
    public void drained(long generation) {
        drained.set(generation);
    }
}
