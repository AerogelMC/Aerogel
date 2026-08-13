package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Fired when the client reports that it stopped mining; this does not imply the block broke. */
public final class BlockMiningStopEvent extends PlayerBlockEvent implements CancellableEvent {
    private final Direction face;
    private final int sequence;
    private boolean cancelled;

    public BlockMiningStopEvent(
        ServerPlayer player, ServerLevel level, BlockPos position, BlockState state,
        Direction face, int sequence
    ) {
        super(player, level, position, state);
        this.face = face;
        this.sequence = sequence;
    }

    public Direction face() { return face; }
    public int sequence() { return sequence; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
