package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.PositionMoveRotation;

public record ClientboundEntityPositionSyncPacket(
    int id, PositionMoveRotation values, boolean onGround
) implements Packet<ClientGamePacketListener> {
}
