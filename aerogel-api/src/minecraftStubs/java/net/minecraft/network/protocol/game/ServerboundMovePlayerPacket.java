package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundMovePlayerPacket implements Packet<Object> {
    public double getX(double fallback) { return fallback; }
    public double getY(double fallback) { return fallback; }
    public double getZ(double fallback) { return fallback; }
    public float getYRot(float fallback) { return fallback; }
    public float getXRot(float fallback) { return fallback; }
    public boolean hasPosition() { return false; }
    public boolean hasRotation() { return false; }
}
