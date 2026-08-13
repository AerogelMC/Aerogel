package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

/** Fired before an entity starts riding another entity. */
public final class EntityMountEvent implements CancellableEvent {
    private final Entity entity;
    private final Entity vehicle;
    private final boolean force;
    private boolean cancelled;

    public EntityMountEvent(Entity entity, Entity vehicle, boolean force) {
        this.entity = entity;
        this.vehicle = vehicle;
        this.force = force;
    }

    public Entity entity() { return entity; }
    public Entity vehicle() { return vehicle; }
    public boolean force() { return force; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
