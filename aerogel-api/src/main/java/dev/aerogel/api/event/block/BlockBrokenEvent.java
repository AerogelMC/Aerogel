package dev.aerogel.api.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Fired after vanilla successfully removes a block for a player. */
public final class BlockBrokenEvent extends PlayerBlockEvent {
    public BlockBrokenEvent(
        ServerPlayer player, ServerLevel level, BlockPos position, BlockState state
    ) {
        super(player, level, position, state);
    }
}
