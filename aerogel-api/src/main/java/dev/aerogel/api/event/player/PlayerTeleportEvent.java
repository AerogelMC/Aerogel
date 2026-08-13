package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;

/** Fired before the common server-side player teleport operation. */
public final class PlayerTeleportEvent implements PlayerEvent, CancellableEvent {
    private final Object playerHandle;
    private final Object destinationLevelHandle;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private boolean cancelled;

    public PlayerTeleportEvent(
        Object playerHandle, Object destinationLevelHandle,
        double x, double y, double z, float yaw, float pitch
    ) {
        this.playerHandle = playerHandle;
        this.destinationLevelHandle = destinationLevelHandle;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override public Object playerHandle() { return playerHandle; }
    @SuppressWarnings("unchecked") public <L> L destinationLevel() { return (L) destinationLevelHandle; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
