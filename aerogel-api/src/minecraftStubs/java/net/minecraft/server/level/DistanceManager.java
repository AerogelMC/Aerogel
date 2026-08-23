package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public abstract class DistanceManager {
    public boolean inEntityTickingRange(long chunkKey) { return false; }
    public void forEachEntityTickingChunk(LongConsumer consumer) { }
    public LongIterator getSpawnCandidateChunks() { return null; }

    public static class FixedPlayerDistanceChunkTracker { }
}
