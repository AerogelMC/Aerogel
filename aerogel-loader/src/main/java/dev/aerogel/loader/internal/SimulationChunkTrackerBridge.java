package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;

public interface SimulationChunkTrackerBridge {
    void aerogel$forEachEntityTickingChunk(LongConsumer consumer);
    boolean aerogel$isEntityTickingChunk(long chunkKey);
    void aerogel$blockTickingListener(LongConsumer listener);
}
