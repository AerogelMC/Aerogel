package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerStoppingEvent(MinecraftServer server) implements ServerEvent {
}
