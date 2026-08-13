package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Fired before any vanilla block-state replacement in a loaded level. */
public final class BlockStateChangeEvent implements CancellableEvent {
    private final Level level;
    private final BlockPos position;
    private final BlockState previousState;
    private final BlockState state;
    private final int flags;
    private final int recursionLeft;
    private boolean cancelled;

    public BlockStateChangeEvent(
        Level level, BlockPos position, BlockState previousState, BlockState state,
        int flags, int recursionLeft
    ) {
        this.level = level;
        this.position = position;
        this.previousState = previousState;
        this.state = state;
        this.flags = flags;
        this.recursionLeft = recursionLeft;
    }

    public Level level() { return level; }
    public BlockPos position() { return position; }
    public BlockState previousState() { return previousState; }
    public BlockState state() { return state; }
    public int flags() { return flags; }
    public int recursionLeft() { return recursionLeft; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
