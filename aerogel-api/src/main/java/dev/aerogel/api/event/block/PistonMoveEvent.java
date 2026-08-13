package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Fired before a piston moves its block line. */
public final class PistonMoveEvent implements CancellableEvent {
    private final Level level;
    private final BlockPos pistonPosition;
    private final Direction direction;
    private final boolean extending;
    private boolean cancelled;

    public PistonMoveEvent(Level level, BlockPos pistonPosition, Direction direction, boolean extending) {
        this.level = level;
        this.pistonPosition = pistonPosition;
        this.direction = direction;
        this.extending = extending;
    }

    public Level level() { return level; }
    public BlockPos pistonPosition() { return pistonPosition; }
    public Direction direction() { return direction; }
    public boolean extending() { return extending; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
