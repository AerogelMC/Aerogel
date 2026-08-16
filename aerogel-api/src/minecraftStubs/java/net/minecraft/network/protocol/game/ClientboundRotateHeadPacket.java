package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

public class ClientboundRotateHeadPacket implements Packet<ClientGamePacketListener> {
    public ClientboundRotateHeadPacket(Entity entity, byte rotation) { }
}
