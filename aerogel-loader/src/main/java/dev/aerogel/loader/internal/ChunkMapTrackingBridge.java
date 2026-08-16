package dev.aerogel.loader.internal;

import net.minecraft.server.level.ServerLevel;

public interface ChunkMapTrackingBridge {
    ServerLevel aerogel$level();
    Object aerogel$trackedEntity(int entityId);
}
