package dev.aerogel.api.event.server;

import net.minecraft.server.MinecraftServer;

public record ServerTickEndEvent(MinecraftServer server) implements ServerEvent {
}
