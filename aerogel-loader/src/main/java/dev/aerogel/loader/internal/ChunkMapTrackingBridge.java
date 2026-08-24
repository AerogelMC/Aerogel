package dev.aerogel.loader.internal;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import dev.aerogel.loader.context.ExactChunkDistanceGraph;

public interface ChunkMapTrackingBridge {
    ServerLevel aerogel$level();
    Object aerogel$trackedEntity(int entityId);
    void aerogel$moveSnapshot(ServerPlayer player, SectionPos section, ChunkPos chunk);
    void aerogel$publishGenerationHolders(ExactChunkDistanceGraph.ChangeBatch changes);
}
