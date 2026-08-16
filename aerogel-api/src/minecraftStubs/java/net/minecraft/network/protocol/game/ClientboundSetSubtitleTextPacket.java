package net.minecraft.network.protocol.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public final class ClientboundSetSubtitleTextPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetSubtitleTextPacket(Component subtitle) { }
}
