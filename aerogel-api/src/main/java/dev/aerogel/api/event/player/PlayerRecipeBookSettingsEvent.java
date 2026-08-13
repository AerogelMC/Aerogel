package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerRecipeBookSettingsEvent extends TypedPlayerPacketEvent<ServerboundRecipeBookChangeSettingsPacket> {
    public PlayerRecipeBookSettingsEvent(ServerPlayer player, ServerboundRecipeBookChangeSettingsPacket packet) { super(player, packet); }
}
