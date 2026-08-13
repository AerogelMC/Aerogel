package dev.aerogel.api.event.server;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.server.MinecraftServer;

/** Base for events carrying the live vanilla MinecraftServer instance. */
public interface ServerEvent extends AerogelEvent {
    MinecraftServer server();
}
