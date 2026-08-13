package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;

/** Fired before a fresh entity is added to a ServerLevel. */
public final class EntitySpawnEvent implements CancellableEvent {
    private final Object levelHandle;
    private final Object entityHandle;
    private boolean cancelled;

    public EntitySpawnEvent(Object levelHandle, Object entityHandle) {
        this.levelHandle = levelHandle;
        this.entityHandle = entityHandle;
    }

    @SuppressWarnings("unchecked") public <L> L level() { return (L) levelHandle; }
    @SuppressWarnings("unchecked") public <E> E entity() { return (E) entityHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
