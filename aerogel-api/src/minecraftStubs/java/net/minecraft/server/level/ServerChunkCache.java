package net.minecraft.server.level;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ServerChunkCache {
    public final ChunkMap chunkMap = null;
    public ChunkGenerator getGenerator() { return null; }
    public LevelChunk getChunkNow(int x, int z) { return null; }
    public boolean hasChunk(int x, int z) { return false; }
    public LevelLightEngine getLightEngine() { return null; }
}
