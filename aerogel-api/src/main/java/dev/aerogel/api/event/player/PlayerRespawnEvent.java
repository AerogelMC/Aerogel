package dev.aerogel.api.event.player;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.server.level.ServerPlayer;

/** Fired after vanilla creates and places the replacement ServerPlayer. */
public record PlayerRespawnEvent(
    ServerPlayer previousPlayer, ServerPlayer player, boolean keepEverything
) implements PlayerEvent {
}
