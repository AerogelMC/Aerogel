package net.minecraft.network.protocol;

import net.minecraft.network.PacketListener;

public abstract class BundlePacket<T extends PacketListener> implements Packet<T> {
    public Iterable<Packet<? super T>> subPackets() { return null; }
}
