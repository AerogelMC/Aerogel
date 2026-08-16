package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;

public class ClientboundAddEntityPacket implements Packet<ClientGamePacketListener> {
    public ClientboundAddEntityPacket(Entity entity, int data, BlockPos position) { }
}
