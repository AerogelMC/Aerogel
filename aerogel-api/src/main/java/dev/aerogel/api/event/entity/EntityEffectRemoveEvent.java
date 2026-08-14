package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Fired before one active effect is removed from a living entity. */
public final class EntityEffectRemoveEvent implements CancellableEvent {
    private final LivingEntity entity;
    private Holder<MobEffect> effect;
    private boolean cancelled;

    public EntityEffectRemoveEvent(LivingEntity entity, Holder<MobEffect> effect) {
        this.entity = entity;
        this.effect = Objects.requireNonNull(effect, "effect");
    }

    public LivingEntity entity() { return entity; }
    public Holder<MobEffect> effect() { return effect; }
    public void setEffect(Holder<MobEffect> effect) {
        this.effect = Objects.requireNonNull(effect, "effect");
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
