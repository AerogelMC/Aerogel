package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.CompletableFuture;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public abstract class GenerationChunkHolder {
    public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = null;
    public ChunkPos getPos() { return null; }
    public int getQueueLevel() { return 0; }
    public ChunkStatus getPersistedStatus() { return null; }
    public ChunkAccess getLatestChunk() { return null; }
    public void increaseGenerationRefCount() { }
}
