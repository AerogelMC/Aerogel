package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerTickStartEvent(MinecraftServer server) implements ServerEvent {
}
