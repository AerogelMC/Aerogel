package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Fired before the common server-side player teleport operation. */
public final class PlayerTeleportEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final ServerLevel previousLevel;
    private final double previousX;
    private final double previousY;
    private final double previousZ;
    private final float previousYaw;
    private final float previousPitch;
    private ServerLevel destinationLevel;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean cancelled;

    public PlayerTeleportEvent(
        ServerPlayer player, ServerLevel destinationLevel,
        double x, double y, double z, float yaw, float pitch
    ) {
        this.player = Objects.requireNonNull(player, "player");
        previousLevel = player.level();
        previousX = player.getX();
        previousY = player.getY();
        previousZ = player.getZ();
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        this.destinationLevel = Objects.requireNonNull(destinationLevel, "destinationLevel");
        setDestination(x, y, z, yaw, pitch);
    }

    @Override public ServerPlayer player() { return player; }
    public ServerLevel previousLevel() { return previousLevel; }
    public double previousX() { return previousX; }
    public double previousY() { return previousY; }
    public double previousZ() { return previousZ; }
    public float previousYaw() { return previousYaw; }
    public float previousPitch() { return previousPitch; }
    public ServerLevel destinationLevel() { return destinationLevel; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public void setDestinationLevel(ServerLevel destinationLevel) {
        this.destinationLevel = Objects.requireNonNull(destinationLevel, "destinationLevel");
    }
    public void setPosition(double x, double y, double z) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public void setRotation(float yaw, float pitch) {
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
        this.yaw = yaw;
        this.pitch = pitch;
    }
    public void setDestination(double x, double y, double z, float yaw, float pitch) {
        setPosition(x, y, z);
        setRotation(yaw, pitch);
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
