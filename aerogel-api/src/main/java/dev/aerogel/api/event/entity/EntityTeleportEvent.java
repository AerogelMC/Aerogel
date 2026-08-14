package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.Objects;

/** Fired before a non-player-specific entity teleport transition is applied. */
public final class EntityTeleportEvent implements CancellableEvent {
    private final Entity entity;
    private TeleportTransition transition;
    private boolean cancelled;

    public EntityTeleportEvent(Entity entity, TeleportTransition transition) {
        this.entity = entity;
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    public Entity entity() { return entity; }
    public TeleportTransition transition() { return transition; }
    public void setTransition(TeleportTransition transition) {
        this.transition = Objects.requireNonNull(transition, "transition");
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
