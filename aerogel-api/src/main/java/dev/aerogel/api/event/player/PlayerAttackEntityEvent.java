package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a player attack packet is validated and applied to its entity target. */
public final class PlayerAttackEntityEvent extends TypedPlayerPacketEvent<ServerboundAttackPacket> {
    public PlayerAttackEntityEvent(ServerPlayer player, ServerboundAttackPacket packet) { super(player, packet); }
}
