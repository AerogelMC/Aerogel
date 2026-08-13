package dev.aerogel.api.event.block;

import dev.aerogel.api.event.CancellableEvent;

/** Fired immediately before a player attempts to destroy a block. */
public final class BlockBreakEvent implements CancellableEvent {
    private final Object playerHandle;
    private final Object levelHandle;
    private final Object positionHandle;
    private boolean cancelled;

    public BlockBreakEvent(Object playerHandle, Object levelHandle, Object positionHandle) {
        this.playerHandle = playerHandle;
        this.levelHandle = levelHandle;
        this.positionHandle = positionHandle;
    }

    @SuppressWarnings("unchecked") public <P> P player() { return (P) playerHandle; }
    @SuppressWarnings("unchecked") public <L> L level() { return (L) levelHandle; }
    @SuppressWarnings("unchecked") public <P> P position() { return (P) positionHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
