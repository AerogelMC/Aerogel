package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a player interaction with an entity is resolved. */
public final class PlayerInteractEntityEvent extends TypedPlayerPacketEvent<ServerboundInteractPacket> {
    public PlayerInteractEntityEvent(ServerPlayer player, ServerboundInteractPacket packet) { super(player, packet); }
}
