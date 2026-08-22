package net.minecraft.network.protocol;

import net.minecraft.network.PacketListener;

public abstract class BundleDelimiterPacket<T extends PacketListener> implements Packet<T> {
}
