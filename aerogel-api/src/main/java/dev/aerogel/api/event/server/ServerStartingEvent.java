package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerStartingEvent(MinecraftServer server) implements ServerEvent {
}
