package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ClientboundPlayerInfoUpdatePacket implements Packet<ClientGamePacketListener> {
    public ClientboundPlayerInfoUpdatePacket(Action action, ServerPlayer player) { }

    public static ClientboundPlayerInfoUpdatePacket createPlayerInitializing(Collection<ServerPlayer> players) {
        return null;
    }

    public enum Action {
        ADD_PLAYER
    }
}
