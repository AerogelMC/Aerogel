package dev.aerogel.api.event.server;

public record ServerStoppedEvent(Object serverHandle) implements ServerEvent {
}
