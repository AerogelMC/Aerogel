package dev.aerogel.api.event.world;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

public final class ExplosionEvent implements WorldEvent, CancellableEvent {
    private final ServerLevel level;
    private final Entity source;
    private double x;
    private double y;
    private double z;
    private float radius;
    private boolean fire;
    private boolean cancelled;

    public ExplosionEvent(
        ServerLevel level, Entity source, double x, double y, double z, float radius, boolean fire
    ) {
        this.level = level;
        this.source = source;
        setPosition(x, y, z);
        setRadius(radius);
        this.fire = fire;
    }

    @Override public ServerLevel level() { return level; }
    public Entity source() { return source; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float radius() { return radius; }
    public void setPosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("explosion position must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public void setRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("radius must be finite and not negative");
        }
        this.radius = radius;
    }
    public boolean fire() { return fire; }
    public void setFire(boolean fire) { this.fire = fire; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
