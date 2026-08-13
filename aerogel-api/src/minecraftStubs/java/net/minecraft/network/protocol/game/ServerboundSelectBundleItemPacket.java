package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundSelectBundleItemPacket implements Packet<Object> {
    public int slotId() { return 0; }
    public int selectedItemIndex() { return 0; }
}
