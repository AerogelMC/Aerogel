package dev.aerogel.api.event.server;

public record ServerTickStartEvent(Object serverHandle) implements ServerEvent {
}
