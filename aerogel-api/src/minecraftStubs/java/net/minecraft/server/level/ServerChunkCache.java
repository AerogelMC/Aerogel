package net.minecraft.server.level;

import net.minecraft.core.BlockPos;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import java.util.concurrent.CompletableFuture;

public class ServerChunkCache {
    public void blockChanged(BlockPos position) { }
    public final ChunkMap chunkMap = null;
    public ChunkGenerator getGenerator() { return null; }
    public LevelChunk getChunkNow(int x, int z) { return null; }
    public boolean hasChunk(int x, int z) { return false; }
    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(
        int x, int z, ChunkStatus status, boolean create
    ) { return null; }
    public void move(ServerPlayer player) { }
    public LevelLightEngine getLightEngine() { return null; }
}
