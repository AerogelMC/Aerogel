package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before edited sign text is validated and stored. */
public final class PlayerSignUpdateEvent extends TypedPlayerPacketEvent<ServerboundSignUpdatePacket> {
    public PlayerSignUpdateEvent(ServerPlayer player, ServerboundSignUpdatePacket packet) { super(player, packet); }
}
