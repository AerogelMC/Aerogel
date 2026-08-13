package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerResourcePackStatusEvent extends TypedPlayerPacketEvent<ServerboundResourcePackPacket> {
    public PlayerResourcePackStatusEvent(ServerPlayer player, ServerboundResourcePackPacket packet) { super(player, packet); }
}
