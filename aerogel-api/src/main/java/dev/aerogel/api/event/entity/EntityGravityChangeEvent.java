package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Fired before an entity's no-gravity flag changes. */
public final class EntityGravityChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final boolean previouslyNoGravity;
    private boolean noGravity;
    private boolean cancelled;

    public EntityGravityChangeEvent(
        Entity entity, boolean previouslyNoGravity, boolean noGravity
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previouslyNoGravity = previouslyNoGravity;
        this.noGravity = noGravity;
    }

    public Entity entity() { return entity; }
    public boolean previouslyNoGravity() { return previouslyNoGravity; }
    public boolean noGravity() { return noGravity; }
    public void setNoGravity(boolean noGravity) { this.noGravity = noGravity; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
