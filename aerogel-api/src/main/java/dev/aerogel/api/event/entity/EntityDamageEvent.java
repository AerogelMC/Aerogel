package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;

/** Fired before a LivingEntity processes server-side damage. */
public final class EntityDamageEvent implements CancellableEvent {
    private final Object entityHandle;
    private final Object levelHandle;
    private final Object damageSourceHandle;
    private final float amount;
    private boolean cancelled;

    public EntityDamageEvent(Object entityHandle, Object levelHandle, Object damageSourceHandle, float amount) {
        this.entityHandle = entityHandle;
        this.levelHandle = levelHandle;
        this.damageSourceHandle = damageSourceHandle;
        this.amount = amount;
    }

    @SuppressWarnings("unchecked") public <E> E entity() { return (E) entityHandle; }
    @SuppressWarnings("unchecked") public <L> L level() { return (L) levelHandle; }
    @SuppressWarnings("unchecked") public <D> D damageSource() { return (D) damageSourceHandle; }
    public float amount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
