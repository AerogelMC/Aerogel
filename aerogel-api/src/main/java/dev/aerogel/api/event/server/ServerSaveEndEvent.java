package dev.aerogel.api.event.server;

public record ServerSaveEndEvent(
    Object serverHandle, boolean suppressLog, boolean flush, boolean force, boolean successful
) implements ServerEvent {
}
