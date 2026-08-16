package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.Vec3;

public record ClientboundSetEntityMotionPacket(int id, Vec3 movement)
    implements Packet<ClientGamePacketListener> {
}
