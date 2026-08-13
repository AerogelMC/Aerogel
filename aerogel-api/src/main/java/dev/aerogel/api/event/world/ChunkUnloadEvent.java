package dev.aerogel.api.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Fired after a full chunk has been detached from its server level. */
public record ChunkUnloadEvent(ServerLevel level, LevelChunk chunk) implements WorldEvent {
}
