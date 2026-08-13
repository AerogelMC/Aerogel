package dev.aerogel.api.event.server;

public record ServerStoppingEvent(Object serverHandle) implements ServerEvent {
}
