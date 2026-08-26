package dev.aerogel.loader.context;

/** Tracks causal continuations detached from a neighbor updater's source task. */
public interface NeighborUpdaterAsyncBridge {
    void aerogel$beginAsyncNeighborContinuation();
    void aerogel$endAsyncNeighborContinuation();
    boolean aerogel$hasAsyncNeighborContinuation();
    void aerogel$neighborCausalGroup(NeighborCausalGroup group);
}
