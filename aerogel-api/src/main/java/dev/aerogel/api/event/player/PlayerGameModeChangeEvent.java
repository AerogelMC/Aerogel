package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;

/** Fired before vanilla changes a player's game mode. */
public final class PlayerGameModeChangeEvent implements PlayerEvent, CancellableEvent {
    private final Object playerHandle;
    private final Object gameModeHandle;
    private boolean cancelled;

    public PlayerGameModeChangeEvent(Object playerHandle, Object gameModeHandle) {
        this.playerHandle = playerHandle;
        this.gameModeHandle = gameModeHandle;
    }

    @Override public Object playerHandle() { return playerHandle; }
    @SuppressWarnings("unchecked") public <G> G gameMode() { return (G) gameModeHandle; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
