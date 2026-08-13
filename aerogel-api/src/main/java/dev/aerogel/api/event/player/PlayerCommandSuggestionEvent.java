package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerCommandSuggestionEvent extends TypedPlayerPacketEvent<ServerboundCommandSuggestionPacket> {
    public PlayerCommandSuggestionEvent(ServerPlayer player, ServerboundCommandSuggestionPacket packet) { super(player, packet); }
}
