package dev.aerogel.loader.internal;

import dev.aerogel.loader.context.ExactChunkDistanceGraph;

public interface ExactChunkTrackerBridge {
    void aerogel$updateSource(long chunkKey, int level);
    int aerogel$runExactUpdates(
        int maximumUpdates, ExactChunkDistanceGraph.LevelPublisher publisher);
}
