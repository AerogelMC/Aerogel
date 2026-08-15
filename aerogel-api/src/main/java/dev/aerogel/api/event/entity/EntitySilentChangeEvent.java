package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Fired before an entity's silent flag changes. */
public final class EntitySilentChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final boolean previouslySilent;
    private boolean silent;
    private boolean cancelled;

    public EntitySilentChangeEvent(Entity entity, boolean previouslySilent, boolean silent) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previouslySilent = previouslySilent;
        this.silent = silent;
    }

    public Entity entity() { return entity; }
    public boolean previouslySilent() { return previouslySilent; }
    public boolean silent() { return silent; }
    public void setSilent(boolean silent) { this.silent = silent; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
