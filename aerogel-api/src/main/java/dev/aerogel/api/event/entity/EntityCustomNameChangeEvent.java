package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Fired before an entity's custom name changes. A null name clears it. */
public final class EntityCustomNameChangeEvent implements CancellableEvent {
    private final Entity entity;
    private final Component previous;
    private Component customName;
    private boolean cancelled;

    public EntityCustomNameChangeEvent(Entity entity, Component previous, Component customName) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previous = previous;
        this.customName = customName;
    }

    public Entity entity() { return entity; }
    public Component previous() { return previous; }
    public Component customName() { return customName; }
    public void setCustomName(Component customName) { this.customName = customName; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
