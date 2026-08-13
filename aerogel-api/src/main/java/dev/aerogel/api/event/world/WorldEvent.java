package dev.aerogel.api.event.world;

import dev.aerogel.api.event.AerogelEvent;

public interface WorldEvent extends AerogelEvent {
    Object levelHandle();

    @SuppressWarnings("unchecked")
    default <L> L level() { return (L) levelHandle(); }
}
