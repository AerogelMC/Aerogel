package dev.aerogel.api.event.world;

import dev.aerogel.api.event.CancellableEvent;

public final class ExplosionEvent implements WorldEvent, CancellableEvent {
    private final Object levelHandle;
    private final Object sourceHandle;
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private boolean cancelled;

    public ExplosionEvent(Object levelHandle, Object sourceHandle, double x, double y, double z, float radius) {
        this.levelHandle = levelHandle;
        this.sourceHandle = sourceHandle;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    @Override public Object levelHandle() { return levelHandle; }
    @SuppressWarnings("unchecked") public <E> E source() { return (E) sourceHandle; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float radius() { return radius; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
