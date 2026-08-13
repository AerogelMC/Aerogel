package dev.aerogel.api.event.player;

import dev.aerogel.api.event.AerogelEvent;

/** Fired after vanilla creates and places the replacement ServerPlayer. */
public record PlayerRespawnEvent(
    Object previousPlayerHandle, Object playerHandle, boolean keepEverything
) implements PlayerEvent {
    @SuppressWarnings("unchecked") public <P> P previousPlayer() { return (P) previousPlayerHandle; }
}
