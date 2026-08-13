package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before the server validates movement of the vehicle controlled by a player. */
public final class PlayerVehicleMoveEvent extends TypedPlayerPacketEvent<ServerboundMoveVehiclePacket> {
    public PlayerVehicleMoveEvent(ServerPlayer player, ServerboundMoveVehiclePacket packet) { super(player, packet); }
}
