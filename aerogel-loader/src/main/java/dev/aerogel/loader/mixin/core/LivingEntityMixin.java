package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityDamageEvent;
import dev.aerogel.api.event.entity.EntityDeathEvent;
import dev.aerogel.api.event.entity.EntityEffectAddEvent;
import dev.aerogel.api.event.entity.EntityHealEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.LivingEntity")
abstract class LivingEntityMixin {
    @Inject(
        method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeDamage(
        @Coerce Object level,
        @Coerce Object source,
        float amount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EntityDamageEvent event = new EntityDamageEvent(this, level, source, amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
        method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
        at = @At("HEAD")
    )
    private void aerogel$entityDied(@Coerce Object source, CallbackInfo callbackInfo) {
        EventHooks.post(new EntityDeathEvent(this, source));
    }

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$heal(float amount, CallbackInfo callbackInfo) {
        EntityHealEvent event = new EntityHealEvent(this, amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;"
        + "Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$effectAdded(
        @Coerce Object effect, @Coerce Object source, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EntityEffectAddEvent event = new EntityEffectAddEvent(this, effect, source);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
