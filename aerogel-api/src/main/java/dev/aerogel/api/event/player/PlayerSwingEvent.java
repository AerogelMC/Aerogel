package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a hand-swing animation is broadcast. */
public final class PlayerSwingEvent extends TypedPlayerPacketEvent<ServerboundSwingPacket> {
    public PlayerSwingEvent(ServerPlayer player, ServerboundSwingPacket packet) { super(player, packet); }
}
