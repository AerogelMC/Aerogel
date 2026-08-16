package net.minecraft.network.protocol.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

public final class ClientboundSetTitleTextPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetTitleTextPacket(Component title) { }
}
