package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerStoppedEvent(MinecraftServer server) implements ServerEvent {
}
