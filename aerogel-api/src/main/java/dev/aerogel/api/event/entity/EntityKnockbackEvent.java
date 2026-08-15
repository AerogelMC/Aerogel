package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Fired before vanilla applies knockback to a living entity. */
public final class EntityKnockbackEvent implements CancellableEvent {
    private final LivingEntity entity;
    private double strength;
    private double directionX;
    private double directionZ;
    private DamageSource damageSource;
    private float verticalStrength;
    private boolean limitVertical;
    private boolean cancelled;

    public EntityKnockbackEvent(
        LivingEntity entity,
        double strength,
        double directionX,
        double directionZ,
        DamageSource damageSource,
        float verticalStrength,
        boolean limitVertical
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        setStrength(strength);
        setDirection(directionX, directionZ);
        this.damageSource = Objects.requireNonNull(damageSource, "damageSource");
        setVerticalStrength(verticalStrength);
        this.limitVertical = limitVertical;
    }

    public LivingEntity entity() { return entity; }
    public double strength() { return strength; }
    public void setStrength(double strength) {
        if (!Double.isFinite(strength)) {
            throw new IllegalArgumentException("strength must be finite");
        }
        this.strength = strength;
    }
    public double directionX() { return directionX; }
    public double directionZ() { return directionZ; }
    public void setDirection(double directionX, double directionZ) {
        if (!Double.isFinite(directionX) || !Double.isFinite(directionZ)) {
            throw new IllegalArgumentException("direction components must be finite");
        }
        this.directionX = directionX;
        this.directionZ = directionZ;
    }
    public DamageSource damageSource() { return damageSource; }
    public void setDamageSource(DamageSource damageSource) {
        this.damageSource = Objects.requireNonNull(damageSource, "damageSource");
    }
    public float verticalStrength() { return verticalStrength; }
    public void setVerticalStrength(float verticalStrength) {
        if (!Float.isFinite(verticalStrength)) {
            throw new IllegalArgumentException("verticalStrength must be finite");
        }
        this.verticalStrength = verticalStrength;
    }
    public boolean limitVertical() { return limitVertical; }
    public void setLimitVertical(boolean limitVertical) { this.limitVertical = limitVertical; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
