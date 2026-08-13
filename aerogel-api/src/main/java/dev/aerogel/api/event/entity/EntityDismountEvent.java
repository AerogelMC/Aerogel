package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

/** Fired before an entity stops riding its current vehicle. */
public final class EntityDismountEvent implements CancellableEvent {
    private final Entity entity;
    private final Entity vehicle;
    private boolean cancelled;

    public EntityDismountEvent(Entity entity, Entity vehicle) {
        this.entity = entity;
        this.vehicle = vehicle;
    }

    public Entity entity() { return entity; }
    public Entity vehicle() { return vehicle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
