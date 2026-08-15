package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired before any vanilla block-state replacement in a loaded level.
 * This includes placement, removal, growth, fluids, explosions, pistons,
 * and mob-driven changes such as an enderman taking or placing a block.
 *
 * <p>{@link #reason()} describes the operation that initiated the change. Known
 * vanilla operations also expose their actor through {@link #sourceEntity()},
 * their originating block through {@link #sourcePosition()}, or their precise
 * origin through {@link #sourceLocation()}. A direct call to a vanilla block
 * setter has {@link Reason#DIRECT} because no broader operation exists.</p>
 */
public final class BlockStateChangeEvent implements CancellableEvent {
    public enum ChangeType {
        PLACE,
        REMOVE,
        REPLACE
    }

    public enum Reason {
        PLAYER_PLACE,
        PLAYER_BREAK,
        PLAYER_INTERACTION,
        ENTITY_ACTION,
        EXPLOSION,
        PISTON,
        FLUID,
        RANDOM_TICK,
        SCHEDULED_TICK,
        DIRECT
    }

    private final Level level;
    private final BlockPos position;
    private final BlockState previousState;
    private final Reason reason;
    private final Entity sourceEntity;
    private final BlockPos sourcePosition;
    private final Vec3 sourceLocation;
    private BlockState state;
    private int flags;
    private int recursionLeft;
    private boolean cancelled;

    public BlockStateChangeEvent(
        Level level, BlockPos position, BlockState previousState, BlockState state,
        int flags, int recursionLeft
    ) {
        this(level, position, previousState, state, flags, recursionLeft,
            Reason.DIRECT, null, null, null);
    }

    public BlockStateChangeEvent(
        Level level, BlockPos position, BlockState previousState, BlockState state,
        int flags, int recursionLeft, Reason reason, Entity sourceEntity,
        BlockPos sourcePosition, Vec3 sourceLocation
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.position = Objects.requireNonNull(position, "position");
        this.previousState = Objects.requireNonNull(previousState, "previousState");
        this.state = Objects.requireNonNull(state, "state");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.sourceEntity = sourceEntity;
        this.sourcePosition = sourcePosition;
        this.sourceLocation = sourceLocation;
        this.flags = flags;
        setRecursionLeft(recursionLeft);
    }

    public Level level() { return level; }
    public BlockPos position() { return position; }
    public BlockState previousState() { return previousState; }
    /** Returns the effective change type, including a state replacement made by an earlier listener. */
    public ChangeType changeType() { return determineChangeType(previousState, state); }
    /** Returns the exact known vanilla operation that initiated this state change. */
    public Reason reason() { return reason; }
    /** Returns the player or entity responsible for the operation, when one exists. */
    public Optional<Entity> sourceEntity() { return Optional.ofNullable(sourceEntity); }
    /** Returns the operation's originating block, not the changed block, when one exists. */
    public Optional<BlockPos> sourcePosition() { return Optional.ofNullable(sourcePosition); }
    /** Returns the operation's precise world-space origin, when one exists. */
    public Optional<Vec3> sourceLocation() { return Optional.ofNullable(sourceLocation); }
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

    private static ChangeType determineChangeType(BlockState previousState, BlockState state) {
        if (previousState.isAir() && !state.isAir()) return ChangeType.PLACE;
        if (!previousState.isAir() && state.isAir()) return ChangeType.REMOVE;
        return ChangeType.REPLACE;
    }
}
