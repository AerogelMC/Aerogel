package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired for player state actions such as starting sneak, sprint, or gliding. */
public final class PlayerCommandActionEvent extends TypedPlayerPacketEvent<ServerboundPlayerCommandPacket> {
    public PlayerCommandActionEvent(ServerPlayer player, ServerboundPlayerCommandPacket packet) { super(player, packet); }
}
