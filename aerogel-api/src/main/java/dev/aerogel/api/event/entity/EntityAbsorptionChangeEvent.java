package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Fired before a living entity's absorption amount changes. */
public final class EntityAbsorptionChangeEvent implements CancellableEvent {
    private final LivingEntity entity;
    private final float previous;
    private float absorption;
    private boolean cancelled;

    public EntityAbsorptionChangeEvent(
        LivingEntity entity, float previous, float absorption
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.previous = previous;
        setAbsorption(absorption);
    }

    public LivingEntity entity() { return entity; }
    public float previous() { return previous; }
    public float absorption() { return absorption; }
    public void setAbsorption(float absorption) {
        if (!Float.isFinite(absorption)) {
            throw new IllegalArgumentException("absorption must be finite");
        }
        this.absorption = absorption;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
