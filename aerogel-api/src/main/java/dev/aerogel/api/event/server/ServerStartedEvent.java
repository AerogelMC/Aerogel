package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerStartedEvent(MinecraftServer server) implements ServerEvent {
}
