package dev.aerogel.api.blockbatch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public interface BlockBatch {
    ServerLevel level();
    BlockBatch set(BlockPos position, BlockState state);
    BlockBatch setAll(Map<BlockPos, BlockState> changes);
    int size();
    void clear();
    default BlockBatchResult commit() { return commit(BlockBatchOptions.defaults()); }
    BlockBatchResult commit(BlockBatchOptions options);
}
