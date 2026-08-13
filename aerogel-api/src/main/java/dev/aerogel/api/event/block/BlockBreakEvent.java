package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Fired after vanilla approves destruction and immediately before it removes the block. */
public final class BlockBreakEvent extends PlayerBlockEvent implements CancellableEvent {
    private boolean cancelled;

    public BlockBreakEvent(ServerPlayer player, ServerLevel level, BlockPos position) {
        this(player, level, position, level.getBlockState(position));
    }

    public BlockBreakEvent(
        ServerPlayer player, ServerLevel level, BlockPos position, BlockState state
    ) {
        super(player, level, position, state);
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
