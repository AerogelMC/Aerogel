package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.util.TriState;

public interface NaturalSpawnDistanceBridge {
    TriState aerogel$publishedPlayersNearby(long chunkKey);
    long aerogel$spawnDistanceVersion();
    void aerogel$spawnDistanceListener(LongConsumer listener);
}
