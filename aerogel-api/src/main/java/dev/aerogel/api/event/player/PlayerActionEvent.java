package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerActionEvent extends PlayerPacketEvent {
    public PlayerActionEvent(ServerPlayer player, ServerboundPlayerActionPacket packet) { super(player, packet); }
    @Override public ServerboundPlayerActionPacket packet() {
        return (ServerboundPlayerActionPacket) super.packet();
    }
}
