package dev.aerogel.api.event.server;

public record ServerSaveStartEvent(
    Object serverHandle, boolean suppressLog, boolean flush, boolean force
) implements ServerEvent {
}
