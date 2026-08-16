package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

public class ClientboundEntityEventPacket implements Packet<ClientGamePacketListener> {
    public ClientboundEntityEventPacket(Entity entity, byte eventId) { }
}
