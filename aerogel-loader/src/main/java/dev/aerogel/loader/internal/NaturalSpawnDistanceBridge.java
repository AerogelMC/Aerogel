package dev.aerogel.loader.internal;

import net.minecraft.util.TriState;

public interface NaturalSpawnDistanceBridge {
    TriState aerogel$publishedPlayersNearby(long chunkKey);
    long aerogel$spawnDistanceVersion();
}
