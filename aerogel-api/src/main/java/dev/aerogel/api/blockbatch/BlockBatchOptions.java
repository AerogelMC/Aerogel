package dev.aerogel.api.blockbatch;

import net.minecraft.world.level.block.Block;

/** Controls vanilla update semantics while coalescing client synchronization by chunk. */
public record BlockBatchOptions(int updateFlags, boolean rollbackOnFailure, boolean requireLoadedChunks) {
    public static BlockBatchOptions defaults() {
        return new BlockBatchOptions(Block.UPDATE_ALL, true, true);
    }
}
