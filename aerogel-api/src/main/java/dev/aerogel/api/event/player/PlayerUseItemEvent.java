package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerUseItemEvent extends PlayerPacketEvent {
    public PlayerUseItemEvent(ServerPlayer player, ServerboundUseItemPacket packet) { super(player, packet); }
    @Override public ServerboundUseItemPacket packet() {
        return (ServerboundUseItemPacket) super.packet();
    }
}
