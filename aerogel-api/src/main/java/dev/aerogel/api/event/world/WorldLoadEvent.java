package dev.aerogel.api.event.world;

import net.minecraft.server.level.ServerLevel;

public record WorldLoadEvent(ServerLevel level) implements WorldEvent {
}
