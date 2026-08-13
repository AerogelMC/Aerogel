package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class EntityEffectAddEvent implements CancellableEvent {
    private final LivingEntity entity;
    private final MobEffectInstance effect;
    private final Entity source;
    private boolean cancelled;

    public EntityEffectAddEvent(LivingEntity entity, MobEffectInstance effect, Entity source) {
        this.entity = entity;
        this.effect = effect;
        this.source = source;
    }

    public LivingEntity entity() { return entity; }
    public MobEffectInstance effect() { return effect; }
    public Entity source() { return source; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
