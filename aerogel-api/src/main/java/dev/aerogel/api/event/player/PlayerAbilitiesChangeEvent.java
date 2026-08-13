package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before client-controlled ability changes, including flight state, are applied. */
public final class PlayerAbilitiesChangeEvent extends TypedPlayerPacketEvent<ServerboundPlayerAbilitiesPacket> {
    public PlayerAbilitiesChangeEvent(ServerPlayer player, ServerboundPlayerAbilitiesPacket packet) { super(player, packet); }
}
