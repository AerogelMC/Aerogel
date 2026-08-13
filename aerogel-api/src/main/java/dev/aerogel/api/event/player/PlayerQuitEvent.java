package dev.aerogel.api.event.player;

import net.minecraft.server.level.ServerPlayer;

/** Fired immediately before vanilla removes a player from the server. */
public record PlayerQuitEvent(ServerPlayer player) implements PlayerEvent {
}
