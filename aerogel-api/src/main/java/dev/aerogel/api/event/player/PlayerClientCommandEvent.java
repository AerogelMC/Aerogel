package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerClientCommandEvent extends TypedPlayerPacketEvent<ServerboundClientCommandPacket> {
    public PlayerClientCommandEvent(ServerPlayer player, ServerboundClientCommandPacket packet) { super(player, packet); }
}
