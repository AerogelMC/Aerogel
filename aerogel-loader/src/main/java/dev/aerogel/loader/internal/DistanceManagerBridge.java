package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;

public interface DistanceManagerBridge {
    void aerogel$forEachPublishedEntityTickingChunk(LongConsumer consumer);
    boolean aerogel$isPublishedEntityTickingChunk(long chunkKey);
    void aerogel$blockTickingListener(LongConsumer listener);
}
