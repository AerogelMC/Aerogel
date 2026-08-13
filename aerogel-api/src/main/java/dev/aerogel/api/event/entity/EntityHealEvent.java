package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;

public final class EntityHealEvent implements CancellableEvent {
    private final Object entityHandle;
    private final float amount;
    private boolean cancelled;

    public EntityHealEvent(Object entityHandle, float amount) {
        this.entityHandle = entityHandle;
        this.amount = amount;
    }

    @SuppressWarnings("unchecked") public <E> E entity() { return (E) entityHandle; }
    public float amount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
