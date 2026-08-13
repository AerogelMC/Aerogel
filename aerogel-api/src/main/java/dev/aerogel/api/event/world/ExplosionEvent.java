package dev.aerogel.api.event.world;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

public final class ExplosionEvent implements WorldEvent, CancellableEvent {
    private final ServerLevel level;
    private final Entity source;
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private boolean cancelled;

    public ExplosionEvent(ServerLevel level, Entity source, double x, double y, double z, float radius) {
        this.level = level;
        this.source = source;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    @Override public ServerLevel level() { return level; }
    public Entity source() { return source; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float radius() { return radius; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
