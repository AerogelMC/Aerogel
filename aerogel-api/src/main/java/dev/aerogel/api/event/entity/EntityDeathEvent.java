package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.AerogelEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Fired when a LivingEntity begins its vanilla death handling. */
public record EntityDeathEvent(LivingEntity entity, DamageSource damageSource) implements AerogelEvent {
}
