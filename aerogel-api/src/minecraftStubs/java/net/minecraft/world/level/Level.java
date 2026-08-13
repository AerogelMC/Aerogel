package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public abstract class Level {
    public BlockState getBlockState(BlockPos position) { return null; }
}
