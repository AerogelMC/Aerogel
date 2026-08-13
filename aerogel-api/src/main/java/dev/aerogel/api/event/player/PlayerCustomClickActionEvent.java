package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerCustomClickActionEvent extends TypedPlayerPacketEvent<ServerboundCustomClickActionPacket> {
    public PlayerCustomClickActionEvent(ServerPlayer player, ServerboundCustomClickActionPacket packet) { super(player, packet); }
}
