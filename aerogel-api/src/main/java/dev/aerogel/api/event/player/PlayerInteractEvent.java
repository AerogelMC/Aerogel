package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired before a player's left- or right-click interaction is applied.
 *
 * <p>This is the semantic interaction event. Unlike {@link PlayerSwingEvent}, it does not fire
 * for a swing caused by dropping an item or for another packet action which merely happens to
 * play the hand animation. A left-clicked block is reported from Minecraft's block-action
 * packet, while the per-tick swing animations produced during mining are not misreported as
 * repeated air clicks.</p>
 */
public final class PlayerInteractEvent implements PlayerEvent, CancellableEvent {
    public enum Action {
        LEFT_CLICK,
        RIGHT_CLICK
    }

    public enum Target {
        AIR,
        BLOCK,
        ENTITY
    }

    private final ServerPlayer player;
    private final Action action;
    private final Target target;
    private final InteractionHand hand;
    private final BlockPos blockPosition;
    private final Direction blockFace;
    private final Entity entity;
    private final Vec3 interactionPosition;
    private final boolean secondaryAction;
    private boolean cancelled;

    private PlayerInteractEvent(
        ServerPlayer player,
        Action action,
        Target target,
        InteractionHand hand,
        BlockPos blockPosition,
        Direction blockFace,
        Entity entity,
        Vec3 interactionPosition,
        boolean secondaryAction
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.action = Objects.requireNonNull(action, "action");
        this.target = Objects.requireNonNull(target, "target");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.blockPosition = blockPosition;
        this.blockFace = blockFace;
        this.entity = entity;
        this.interactionPosition = interactionPosition;
        this.secondaryAction = secondaryAction;
    }

    public static PlayerInteractEvent air(
        ServerPlayer player, Action action, InteractionHand hand
    ) {
        return new PlayerInteractEvent(
            player, action, Target.AIR, hand, null, null, null, null, false);
    }

    public static PlayerInteractEvent block(
        ServerPlayer player,
        Action action,
        InteractionHand hand,
        BlockPos position,
        Direction face,
        Vec3 interactionPosition
    ) {
        return new PlayerInteractEvent(
            player, action, Target.BLOCK, hand,
            Objects.requireNonNull(position, "position"),
            Objects.requireNonNull(face, "face"),
            null,
            interactionPosition,
            false);
    }

    public static PlayerInteractEvent entity(
        ServerPlayer player,
        Action action,
        InteractionHand hand,
        Entity entity,
        Vec3 interactionPosition,
        boolean secondaryAction
    ) {
        return new PlayerInteractEvent(
            player, action, Target.ENTITY, hand, null, null,
            Objects.requireNonNull(entity, "entity"), interactionPosition, secondaryAction);
    }

    @Override public ServerPlayer player() { return player; }
    public Action action() { return action; }
    public Target target() { return target; }
    public InteractionHand hand() { return hand; }

    /** The clicked block position, or empty when {@link #target()} is not {@link Target#BLOCK}. */
    public Optional<BlockPos> blockPosition() { return Optional.ofNullable(blockPosition); }

    /** The clicked block face, or empty when {@link #target()} is not {@link Target#BLOCK}. */
    public Optional<Direction> blockFace() { return Optional.ofNullable(blockFace); }

    /** The clicked entity, or empty when {@link #target()} is not {@link Target#ENTITY}. */
    public Optional<Entity> entity() { return Optional.ofNullable(entity); }

    /**
     * The exact block/entity interaction position supplied by the client, when that interaction
     * kind has one. A left-clicked entity has no exact interaction position.
     */
    public Optional<Vec3> interactionPosition() {
        return Optional.ofNullable(interactionPosition);
    }

    /** Whether the client was using its secondary-action modifier for an entity interaction. */
    public boolean isSecondaryAction() { return secondaryAction; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
