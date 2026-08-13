package dev.aerogel.api.event.player;

import net.minecraft.world.damagesource.DamageSource;

/** Fired when a ServerPlayer begins vanilla death handling. */
public record PlayerDeathEvent(
    net.minecraft.server.level.ServerPlayer player, DamageSource damageSource
) implements PlayerEvent {
}
