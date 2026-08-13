package dev.aerogel.api.event.player;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

/** Fired after vanilla finishes placing a player into the server. */
public record PlayerJoinEvent(ServerPlayer player, Connection connection) implements PlayerEvent {
}
