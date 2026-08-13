package dev.aerogel.api.event.player;

/** Fired immediately before vanilla removes a player from the server. */
public record PlayerQuitEvent(Object playerHandle) implements PlayerEvent {
}
