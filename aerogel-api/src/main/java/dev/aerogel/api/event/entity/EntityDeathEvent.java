package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.AerogelEvent;

/** Fired when a LivingEntity begins its vanilla death handling. */
public record EntityDeathEvent(Object entityHandle, Object damageSourceHandle) implements AerogelEvent {
    @SuppressWarnings("unchecked") public <E> E entity() { return (E) entityHandle; }
    @SuppressWarnings("unchecked") public <D> D damageSource() { return (D) damageSourceHandle; }
}
