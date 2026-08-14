package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** Fired before any vanilla block-state replacement in a loaded level. */
public final class BlockStateChangeEvent implements CancellableEvent {
    private final Level level;
    private final BlockPos position;
    private final BlockState previousState;
    private BlockState state;
    private int flags;
    private int recursionLeft;
    private boolean cancelled;

    public BlockStateChangeEvent(
        Level level, BlockPos position, BlockState previousState, BlockState state,
        int flags, int recursionLeft
    ) {
        this.level = level;
        this.position = position;
        this.previousState = previousState;
        this.state = Objects.requireNonNull(state, "state");
        this.flags = flags;
        this.recursionLeft = recursionLeft;
    }

    public Level level() { return level; }
    public BlockPos position() { return position; }
    public BlockState previousState() { return previousState; }
    public BlockState state() { return state; }
    public void setState(BlockState state) { this.state = Objects.requireNonNull(state, "state"); }
    public int flags() { return flags; }
    public void setFlags(int flags) { this.flags = flags; }
    public int recursionLeft() { return recursionLeft; }
    public void setRecursionLeft(int recursionLeft) {
        if (recursionLeft < 0) throw new IllegalArgumentException("recursionLeft must not be negative");
        this.recursionLeft = recursionLeft;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
