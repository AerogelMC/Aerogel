package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundPaddleBoatPacket implements Packet<Object> {
    public boolean getLeft() { return false; }
    public boolean getRight() { return false; }
}
