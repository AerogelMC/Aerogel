package dev.aerogel.api.event.player;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerBundleSelectionEvent extends TypedPlayerPacketEvent<ServerboundSelectBundleItemPacket> {
    public PlayerBundleSelectionEvent(ServerPlayer player, ServerboundSelectBundleItemPacket packet) { super(player, packet); }
}
