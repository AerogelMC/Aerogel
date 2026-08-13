package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerSaveEndEvent(
    MinecraftServer server, boolean suppressLog, boolean flush, boolean force, boolean successful
) implements ServerEvent {
}
