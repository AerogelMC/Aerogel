package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public class ClientboundBundlePacket implements Packet<ClientGamePacketListener> {
    public ClientboundBundlePacket(Iterable<Packet<? super ClientGamePacketListener>> packets) { }
}
