package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerAdvancementsScreenEvent extends TypedPlayerPacketEvent<ServerboundSeenAdvancementsPacket> {
    public PlayerAdvancementsScreenEvent(ServerPlayer player, ServerboundSeenAdvancementsPacket packet) { super(player, packet); }
}
