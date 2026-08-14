package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public class ClientboundRemoveEntitiesPacket implements Packet<ClientGamePacketListener> {
    public ClientboundRemoveEntitiesPacket(int... entityIds) { }
}
