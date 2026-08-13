package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

/** Fired before an entity is ignited for a number of ticks. */
public final class EntityCombustEvent implements CancellableEvent {
    private final Entity entity;
    private final int durationTicks;
    private boolean cancelled;

    public EntityCombustEvent(Entity entity, int durationTicks) {
        this.entity = entity;
        this.durationTicks = durationTicks;
    }

    public Entity entity() { return entity; }
    public int durationTicks() { return durationTicks; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
