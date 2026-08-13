package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before movement-input flags are applied to a player. */
public final class PlayerInputEvent extends TypedPlayerPacketEvent<ServerboundPlayerInputPacket> {
    public PlayerInputEvent(ServerPlayer player, ServerboundPlayerInputPacket packet) { super(player, packet); }
}
