package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Fired whenever the server recalculates a player's block-mining progress. */
public final class BlockMiningProgressEvent extends PlayerBlockEvent implements CancellableEvent {
    private final float progress;
    private final int stage;
    private boolean cancelled;

    public BlockMiningProgressEvent(
        ServerPlayer player, ServerLevel level, BlockPos position, BlockState state,
        float progress, int stage
    ) {
        super(player, level, position, state);
        this.progress = progress;
        this.stage = stage;
    }

    /** Raw accumulated progress; {@code 1.0} or greater is ready to break. */
    public float progress() { return progress; }

    /** Vanilla crack animation stage derived from progress. */
    public int stage() { return stage; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
