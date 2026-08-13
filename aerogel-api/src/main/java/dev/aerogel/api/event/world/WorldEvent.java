package dev.aerogel.api.event.world;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.server.level.ServerLevel;

public interface WorldEvent extends AerogelEvent {
    ServerLevel level();
}
