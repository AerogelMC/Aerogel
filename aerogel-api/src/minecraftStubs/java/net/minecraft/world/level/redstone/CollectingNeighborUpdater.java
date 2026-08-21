package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class CollectingNeighborUpdater {
    private final int maxChainedNeighborUpdates = 0;
    public CollectingNeighborUpdater(Level level, int maxChainedNeighborUpdates) {
    }

    public void setDebugListener(Consumer<BlockPos> listener) {
    }

    public void shapeUpdate(
        Direction direction, BlockState state, BlockPos position, BlockPos neighborPosition,
        int flags, int recursionLeft
    ) {
    }

    public void neighborChanged(BlockPos position, Block block, Orientation orientation) {
    }

    public void neighborChanged(
        BlockState state, BlockPos position, Block block, Orientation orientation, boolean moved
    ) {
    }

    public void updateNeighborsAtExceptFromFacing(
        BlockPos position, Block block, Direction skipDirection, Orientation orientation
    ) {
    }
}
