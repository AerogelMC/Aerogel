package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerUseItemOnBlockEvent extends PlayerPacketEvent {
    public PlayerUseItemOnBlockEvent(ServerPlayer player, ServerboundUseItemOnPacket packet) { super(player, packet); }
    @Override public ServerboundUseItemOnPacket packet() {
        return (ServerboundUseItemOnPacket) super.packet();
    }
}
