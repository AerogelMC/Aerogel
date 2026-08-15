package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Fired before an entity's remaining air supply changes. */
public final class EntityAirSupplyChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final int previous;
    private int airSupply;
    private boolean cancelled;

    public EntityAirSupplyChangeEvent(Entity entity, int previous, int airSupply) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previous = previous;
        setAirSupply(airSupply);
    }

    public Entity entity() { return entity; }
    public int previous() { return previous; }
    public int airSupply() { return airSupply; }
    public void setAirSupply(int airSupply) { this.airSupply = airSupply; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
