package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityDamageEvent;
import dev.aerogel.api.event.entity.EntityDeathEvent;
import dev.aerogel.api.event.entity.EntityEffectAddEvent;
import dev.aerogel.api.event.entity.EntityEffectRemoveEvent;
import dev.aerogel.api.event.entity.EntityEquipmentChangeEvent;
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
        EntityDamageEvent event = new EntityDamageEvent(
            EventHooks.cast(this), EventHooks.cast(level), EventHooks.cast(source), amount);
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
        EventHooks.post(new EntityDeathEvent(EventHooks.cast(this), EventHooks.cast(source)));
    }

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$heal(float amount, CallbackInfo callbackInfo) {
        EntityHealEvent event = new EntityHealEvent(EventHooks.cast(this), amount);
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
        EntityEffectAddEvent event = new EntityEffectAddEvent(
            EventHooks.cast(this), EventHooks.cast(effect), EventHooks.cast(source));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "removeEffect(Lnet/minecraft/core/Holder;)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$effectRemoved(
        @Coerce Object effect, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EntityEffectRemoveEvent event = new EntityEffectRemoveEvent(
            EventHooks.cast(this), EventHooks.cast(effect));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;"
        + "Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$equipmentChanged(
        @Coerce Object slot, @Coerce Object item, CallbackInfo callbackInfo
    ) {
        EntityEquipmentChangeEvent event = new EntityEquipmentChangeEvent(
            EventHooks.cast(this), EventHooks.cast(slot),
            EventHooks.cast(EventHooks.call(this, "getItemBySlot", slot)), EventHooks.cast(item));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }
}
