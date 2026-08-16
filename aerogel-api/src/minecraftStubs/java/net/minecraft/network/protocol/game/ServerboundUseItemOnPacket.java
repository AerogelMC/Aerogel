package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public final class ServerboundUseItemOnPacket implements Packet<Object> {
    public net.minecraft.world.InteractionHand getHand() { return null; }
    public net.minecraft.world.phys.BlockHitResult getHitResult() { return null; }
}
