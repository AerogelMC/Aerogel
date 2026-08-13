package dev.aerogel.api.event.server;

public record ServerStartingEvent(Object serverHandle) implements ServerEvent {
}
