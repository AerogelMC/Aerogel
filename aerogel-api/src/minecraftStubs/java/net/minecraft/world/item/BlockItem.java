package net.minecraft.world.item;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

public class BlockItem {
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) { return false; }
}
