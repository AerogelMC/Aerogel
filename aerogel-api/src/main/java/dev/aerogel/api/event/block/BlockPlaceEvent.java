package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;

public final class BlockPlaceEvent implements CancellableEvent {
    private final Object blockItemHandle;
    private final Object contextHandle;
    private boolean cancelled;

    public BlockPlaceEvent(Object blockItemHandle, Object contextHandle) {
        this.blockItemHandle = blockItemHandle;
        this.contextHandle = contextHandle;
    }

    @SuppressWarnings("unchecked") public <B> B blockItem() { return (B) blockItemHandle; }
    @SuppressWarnings("unchecked") public <C> C context() { return (C) contextHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
