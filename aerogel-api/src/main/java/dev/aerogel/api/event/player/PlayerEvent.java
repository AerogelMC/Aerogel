package dev.aerogel.api.event.player;

import dev.aerogel.api.event.AerogelEvent;

/** Base for events carrying the live vanilla ServerPlayer instance. */
public interface PlayerEvent extends AerogelEvent {
    Object playerHandle();

    @SuppressWarnings("unchecked")
    default <P> P player() {
        return (P) playerHandle();
    }
}
