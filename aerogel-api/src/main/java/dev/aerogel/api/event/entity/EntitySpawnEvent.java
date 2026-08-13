package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Fired before a fresh entity is added to a ServerLevel. */
public final class EntitySpawnEvent implements CancellableEvent {
    private final ServerLevel level;
    private final Entity entity;
    private boolean cancelled;

    public EntitySpawnEvent(ServerLevel level, Entity entity) {
        this.level = level;
        this.entity = entity;
    }

    public ServerLevel level() { return level; }
    public Entity entity() { return entity; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
