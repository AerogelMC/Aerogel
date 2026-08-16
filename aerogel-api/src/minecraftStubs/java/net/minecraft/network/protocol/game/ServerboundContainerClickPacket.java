package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public final class ServerboundContainerClickPacket implements Packet<Object> {
    public int containerId() { return 0; }
    public int stateId() { return 0; }
    public short slotNum() { return 0; }
    public byte buttonNum() { return 0; }
    public net.minecraft.world.inventory.ContainerInput containerInput() { return null; }
}
