package dev.aerogel.api.event.server;

public record ServerStartedEvent(Object serverHandle) implements ServerEvent {
}
