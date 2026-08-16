package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before the server validates and applies a player movement packet. */
public final class PlayerMoveEvent extends TypedPlayerPacketEvent<ServerboundMovePlayerPacket> {
    private final double previousX;
    private final double previousY;
    private final double previousZ;
    private final float previousYaw;
    private final float previousPitch;
    private final double newX;
    private final double newY;
    private final double newZ;
    private final float newYaw;
    private final float newPitch;

    public PlayerMoveEvent(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        super(player, packet);
        previousX = player.getX();
        previousY = player.getY();
        previousZ = player.getZ();
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        newX = packet.getX(previousX);
        newY = packet.getY(previousY);
        newZ = packet.getZ(previousZ);
        newYaw = packet.getYRot(previousYaw);
        newPitch = packet.getXRot(previousPitch);
    }

    public double previousX() { return previousX; }
    public double previousY() { return previousY; }
    public double previousZ() { return previousZ; }
    public float previousYaw() { return previousYaw; }
    public float previousPitch() { return previousPitch; }
    public double newX() { return newX; }
    public double newY() { return newY; }
    public double newZ() { return newZ; }
    public float newYaw() { return newYaw; }
    public float newPitch() { return newPitch; }
    public boolean hasPosition() { return packet().hasPosition(); }
    public boolean hasRotation() { return packet().hasRotation(); }
}
