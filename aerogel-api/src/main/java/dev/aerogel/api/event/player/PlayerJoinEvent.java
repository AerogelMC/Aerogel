package dev.aerogel.api.event.player;

/** Fired after vanilla finishes placing a player into the server. */
public record PlayerJoinEvent(Object playerHandle, Object connectionHandle) implements PlayerEvent {
    @SuppressWarnings("unchecked")
    public <C> C connection() {
        return (C) connectionHandle;
    }
}
