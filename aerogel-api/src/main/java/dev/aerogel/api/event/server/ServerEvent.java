package dev.aerogel.api.event.server;

import dev.aerogel.api.event.AerogelEvent;

/** Base for events carrying the live vanilla MinecraftServer instance. */
public interface ServerEvent extends AerogelEvent {
    Object serverHandle();

    @SuppressWarnings("unchecked")
    default <S> S server() {
        return (S) serverHandle();
    }
}
