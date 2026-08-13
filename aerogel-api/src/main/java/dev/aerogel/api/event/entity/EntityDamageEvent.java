package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Fired before a LivingEntity processes server-side damage. */
public final class EntityDamageEvent implements CancellableEvent {
    private final LivingEntity entity;
    private final ServerLevel level;
    private final DamageSource damageSource;
    private final float amount;
    private boolean cancelled;

    public EntityDamageEvent(LivingEntity entity, ServerLevel level, DamageSource damageSource, float amount) {
        this.entity = entity;
        this.level = level;
        this.damageSource = damageSource;
        this.amount = amount;
    }

    public LivingEntity entity() { return entity; }
    public ServerLevel level() { return level; }
    public DamageSource damageSource() { return damageSource; }
    public float amount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
