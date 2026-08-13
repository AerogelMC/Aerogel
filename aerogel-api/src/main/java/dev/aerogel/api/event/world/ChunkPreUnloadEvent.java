package dev.aerogel.api.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Fired before a full chunk is detached from its server level. */
public record ChunkPreUnloadEvent(ServerLevel level, LevelChunk chunk) implements WorldEvent {
}
