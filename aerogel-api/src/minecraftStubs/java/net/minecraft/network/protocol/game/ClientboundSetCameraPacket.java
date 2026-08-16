package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

public class ClientboundSetCameraPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetCameraPacket(Entity entity) { }
}
