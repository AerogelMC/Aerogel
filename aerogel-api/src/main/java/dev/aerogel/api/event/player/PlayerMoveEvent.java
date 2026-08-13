package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before the server validates and applies a player movement packet. */
public final class PlayerMoveEvent extends TypedPlayerPacketEvent<ServerboundMovePlayerPacket> {
    public PlayerMoveEvent(ServerPlayer player, ServerboundMovePlayerPacket packet) { super(player, packet); }
}
