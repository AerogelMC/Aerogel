package net.minecraft.server.level;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ChunkHolder extends GenerationChunkHolder {
    public net.minecraft.world.level.ChunkPos getPos() { return null; }

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
        return null;
    }
    public LevelChunk getTickingChunk() { return null; }

    public ChunkAccess getChunkIfPresent(ChunkStatus status) {
        return null;
    }
    public int getTicketLevel() { return 0; }
}
