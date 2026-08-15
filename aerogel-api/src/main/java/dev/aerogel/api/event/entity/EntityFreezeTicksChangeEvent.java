package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Fired before an entity's accumulated freezing ticks change. */
public final class EntityFreezeTicksChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final int previous;
    private int frozenTicks;
    private boolean cancelled;

    public EntityFreezeTicksChangeEvent(Entity entity, int previous, int frozenTicks) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previous = previous;
        setFrozenTicks(frozenTicks);
    }

    public Entity entity() { return entity; }
    public int previous() { return previous; }
    public int frozenTicks() { return frozenTicks; }
    public void setFrozenTicks(int frozenTicks) { this.frozenTicks = frozenTicks; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
