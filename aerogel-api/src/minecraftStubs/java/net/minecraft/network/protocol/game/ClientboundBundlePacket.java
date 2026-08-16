package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.BundlePacket;

public class ClientboundBundlePacket extends BundlePacket<ClientGamePacketListener> {
    public ClientboundBundlePacket(Iterable<Packet<? super ClientGamePacketListener>> packets) { }
}
