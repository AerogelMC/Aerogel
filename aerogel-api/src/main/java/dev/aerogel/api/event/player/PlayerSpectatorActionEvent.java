package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerSpectatorActionEvent extends TypedPlayerPacketEvent<ServerboundSpectatorActionPacket> {
    public PlayerSpectatorActionEvent(ServerPlayer player, ServerboundSpectatorActionPacket packet) { super(player, packet); }
}
