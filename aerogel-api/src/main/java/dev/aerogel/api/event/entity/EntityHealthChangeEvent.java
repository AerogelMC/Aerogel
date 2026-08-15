package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Fired before a living entity's health value is assigned. */
public final class EntityHealthChangeEvent implements CancellableEvent {
    private final LivingEntity entity;
    private final float previous;
    private float health;
    private boolean cancelled;

    public EntityHealthChangeEvent(LivingEntity entity, float previous, float health) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previous = previous;
        setHealth(health);
    }

    public LivingEntity entity() { return entity; }
    public float previous() { return previous; }
    public float health() { return health; }
    public void setHealth(float health) {
        if (!Float.isFinite(health)) {
            throw new IllegalArgumentException("health must be finite");
        }
        this.health = health;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
