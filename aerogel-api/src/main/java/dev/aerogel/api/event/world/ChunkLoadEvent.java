package dev.aerogel.api.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Fired after a full chunk begins ticking in a server level. */
public record ChunkLoadEvent(ServerLevel level, LevelChunk chunk) implements WorldEvent {
}
