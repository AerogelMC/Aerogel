package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;

public final class EntityEffectAddEvent implements CancellableEvent {
    private final Object entityHandle;
    private final Object effectHandle;
    private final Object sourceHandle;
    private boolean cancelled;

    public EntityEffectAddEvent(Object entityHandle, Object effectHandle, Object sourceHandle) {
        this.entityHandle = entityHandle;
        this.effectHandle = effectHandle;
        this.sourceHandle = sourceHandle;
    }

    @SuppressWarnings("unchecked") public <E> E entity() { return (E) entityHandle; }
    @SuppressWarnings("unchecked") public <E> E effect() { return (E) effectHandle; }
    @SuppressWarnings("unchecked") public <S> S source() { return (S) sourceHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
