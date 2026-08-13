package dev.aerogel.api.event.item;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;

public final class PlayerDropItemEvent implements PlayerEvent, CancellableEvent {
    private final Object playerHandle;
    private final Object itemStackHandle;
    private final boolean randomThrow;
    private final boolean retainOwnership;
    private boolean cancelled;

    public PlayerDropItemEvent(
        Object playerHandle, Object itemStackHandle, boolean randomThrow, boolean retainOwnership
    ) {
        this.playerHandle = playerHandle;
        this.itemStackHandle = itemStackHandle;
        this.randomThrow = randomThrow;
        this.retainOwnership = retainOwnership;
    }

    @Override public Object playerHandle() { return playerHandle; }
    @SuppressWarnings("unchecked") public <I> I itemStack() { return (I) itemStackHandle; }
    public boolean randomThrow() { return randomThrow; }
    public boolean retainOwnership() { return retainOwnership; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
