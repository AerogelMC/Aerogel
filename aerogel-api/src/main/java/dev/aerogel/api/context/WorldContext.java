package dev.aerogel.api.context;

import net.minecraft.core.BlockPos;

/** Chunk-context index for one live server world. */
public interface WorldContext {
    ChunkContext chunk(int chunkX, int chunkZ);

    default ChunkContext block(BlockPos position) {
        return block(position.getX(), position.getZ());
    }

    default ChunkContext block(int blockX, int blockZ) {
        return chunk(blockX >> 4, blockZ >> 4);
    }

    void executeGlobal(Runnable task);
}
