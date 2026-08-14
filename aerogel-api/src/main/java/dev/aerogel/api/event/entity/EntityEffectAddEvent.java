package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

public final class EntityEffectAddEvent implements CancellableEvent {
    private final LivingEntity entity;
    private MobEffectInstance effect;
    private Entity source;
    private boolean cancelled;

    public EntityEffectAddEvent(LivingEntity entity, MobEffectInstance effect, Entity source) {
        this.entity = entity;
        this.effect = Objects.requireNonNull(effect, "effect");
        this.source = source;
    }

    public LivingEntity entity() { return entity; }
    public MobEffectInstance effect() { return effect; }
    public void setEffect(MobEffectInstance effect) {
        this.effect = Objects.requireNonNull(effect, "effect");
    }
    public Entity source() { return source; }
    public void setSource(Entity source) { this.source = source; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
