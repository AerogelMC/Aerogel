package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
public final class ServerboundInteractPacket implements Packet<Object> {
    public int entityId() { return 0; }
    public InteractionHand hand() { return null; }
    public Vec3 location() { return null; }
    public boolean usingSecondaryAction() { return false; }
}
