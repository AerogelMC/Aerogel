package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Fired before vanilla attempts a LivingEntity random teleport. */
public final class EntityRandomTeleportEvent implements CancellableEvent {
    private final LivingEntity entity;
    private double x;
    private double y;
    private double z;
    private boolean showParticles;
    private boolean cancelled;

    public EntityRandomTeleportEvent(
        LivingEntity entity, double x, double y, double z, boolean showParticles
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        setDestination(x, y, z);
        this.showParticles = showParticles;
    }

    public LivingEntity entity() { return entity; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public void setDestination(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("destination coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public boolean showParticles() { return showParticles; }
    public void setShowParticles(boolean showParticles) { this.showParticles = showParticles; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
