package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface NeighborUpdater {
    Direction[] UPDATE_ORDER = {
        Direction.WEST, Direction.EAST, Direction.DOWN,
        Direction.UP, Direction.NORTH, Direction.SOUTH
    };

    static void executeUpdate(
        Level level, BlockState state, BlockPos position,
        Block sourceBlock, Orientation orientation, boolean moved
    ) { }

    static void executeShapeUpdate(
        LevelAccessor level, Direction direction, BlockPos position,
        BlockPos neighborPosition, BlockState neighborState,
        int flags, int recursionLeft
    ) { }
}
