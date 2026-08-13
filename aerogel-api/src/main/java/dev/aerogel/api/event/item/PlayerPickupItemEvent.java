package dev.aerogel.api.event.item;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;

public final class PlayerPickupItemEvent implements PlayerEvent, CancellableEvent {
    private final Object playerHandle;
    private final Object itemEntityHandle;
    private boolean cancelled;

    public PlayerPickupItemEvent(Object playerHandle, Object itemEntityHandle) {
        this.playerHandle = playerHandle;
        this.itemEntityHandle = itemEntityHandle;
    }

    @Override public Object playerHandle() { return playerHandle; }
    @SuppressWarnings("unchecked") public <I> I itemEntity() { return (I) itemEntityHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
