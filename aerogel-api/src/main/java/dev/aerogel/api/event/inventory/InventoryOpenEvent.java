package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;

public final class InventoryOpenEvent implements PlayerEvent, CancellableEvent {
    private final Object playerHandle;
    private final Object providerHandle;
    private boolean cancelled;

    public InventoryOpenEvent(Object playerHandle, Object providerHandle) {
        this.playerHandle = playerHandle;
        this.providerHandle = providerHandle;
    }

    @Override public Object playerHandle() { return playerHandle; }
    @SuppressWarnings("unchecked") public <P> P provider() { return (P) providerHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
