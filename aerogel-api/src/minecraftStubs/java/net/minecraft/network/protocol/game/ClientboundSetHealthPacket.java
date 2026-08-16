package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public class ClientboundSetHealthPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetHealthPacket(float health, int food, float saturation) { }
}
