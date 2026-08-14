package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.LivingEntity;

public final class EntityHealEvent implements CancellableEvent {
    private final LivingEntity entity;
    private float amount;
    private boolean cancelled;

    public EntityHealEvent(LivingEntity entity, float amount) {
        this.entity = entity;
        this.amount = amount;
    }

    public LivingEntity entity() { return entity; }
    public float amount() { return amount; }
    public void setAmount(float amount) {
        if (!Float.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("amount must be finite and not negative");
        }
        this.amount = amount;
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
