package dev.aerogel.api.event.world;

import net.minecraft.server.level.ServerLevel;

public record WorldUnloadEvent(ServerLevel level) implements WorldEvent {
}
