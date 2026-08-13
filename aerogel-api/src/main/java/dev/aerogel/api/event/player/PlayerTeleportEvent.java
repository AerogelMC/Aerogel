package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Fired before the common server-side player teleport operation. */
public final class PlayerTeleportEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final ServerLevel destinationLevel;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private boolean cancelled;

    public PlayerTeleportEvent(
        ServerPlayer player, ServerLevel destinationLevel,
        double x, double y, double z, float yaw, float pitch
    ) {
        this.player = player;
        this.destinationLevel = destinationLevel;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override public ServerPlayer player() { return player; }
    public ServerLevel destinationLevel() { return destinationLevel; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
