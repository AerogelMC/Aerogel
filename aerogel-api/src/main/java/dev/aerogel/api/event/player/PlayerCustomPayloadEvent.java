package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerCustomPayloadEvent extends TypedPlayerPacketEvent<ServerboundCustomPayloadPacket> {
    public PlayerCustomPayloadEvent(ServerPlayer player, ServerboundCustomPayloadPacket packet) { super(player, packet); }
}
