package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerPaddleBoatEvent extends TypedPlayerPacketEvent<ServerboundPaddleBoatPacket> {
    public PlayerPaddleBoatEvent(ServerPlayer player, ServerboundPaddleBoatPacket packet) { super(player, packet); }
}
