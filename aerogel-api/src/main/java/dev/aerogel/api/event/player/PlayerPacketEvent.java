package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;

/** Base for cancellable events raised before a serverbound player packet is handled. */
public abstract class PlayerPacketEvent implements PlayerEvent, CancellableEvent {
    private final Object playerHandle;
    private final Object packetHandle;
    private boolean cancelled;

    protected PlayerPacketEvent(Object playerHandle, Object packetHandle) {
        this.playerHandle = playerHandle;
        this.packetHandle = packetHandle;
    }

    @Override public final Object playerHandle() { return playerHandle; }
    @SuppressWarnings("unchecked") public final <P> P packet() { return (P) packetHandle; }
    @Override public final boolean isCancelled() { return cancelled; }
    @Override public final void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
