package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerRecipeSeenEvent extends TypedPlayerPacketEvent<ServerboundRecipeBookSeenRecipePacket> {
    public PlayerRecipeSeenEvent(ServerPlayer player, ServerboundRecipeBookSeenRecipePacket packet) { super(player, packet); }
}
