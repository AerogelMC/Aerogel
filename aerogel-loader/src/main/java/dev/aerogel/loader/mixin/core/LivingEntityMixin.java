package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityDamageEvent;
import dev.aerogel.api.event.entity.EntityEffectAddEvent;
import dev.aerogel.api.event.entity.EntityEffectRemoveEvent;
import dev.aerogel.api.event.entity.EntityEquipmentChangeEvent;
import dev.aerogel.api.event.entity.EntityHealEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.DeathDropCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.LivingEntity")
abstract class LivingEntityMixin {
    @Unique private boolean aerogel$damageOverride;
    @Unique private boolean aerogel$healOverride;
    @Unique private boolean aerogel$effectAddOverride;
    @Unique private boolean aerogel$effectRemoveOverride;
    @Unique private boolean aerogel$equipmentOverride;

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
        if (aerogel$damageOverride) return;
        EntityDamageEvent event = new EntityDamageEvent(
            EventHooks.cast(this), EventHooks.cast(level), EventHooks.cast(source), amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.damageSource() != source || Float.compare(event.amount(), amount) != 0) {
            aerogel$damageOverride = true;
            try {
                callbackInfo.setReturnValue((Boolean) EventHooks.call(
                    this, "hurtServer", level, event.damageSource(), event.amount()));
            } finally {
                aerogel$damageOverride = false;
            }
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void aerogel$beginDeathDrops(
        ServerLevel level, @Coerce Object source, CallbackInfo callbackInfo
    ) {
        DeathDropCapture.begin(EventHooks.cast(this), EventHooks.cast(source));
    }

    @Redirect(method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ExperienceOrb;award("
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void aerogel$captureDeathExperience(ServerLevel level, Vec3 position, int amount) {
        if (!DeathDropCapture.captureExperience(amount)) {
            ExperienceOrb.award(level, position, amount);
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void aerogel$completeDeathDrops(
        ServerLevel level, @Coerce Object source, CallbackInfo callbackInfo
    ) {
        DeathDropCapture.complete(level, EventHooks.cast(this), EventHooks.cast(source));
    }

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$heal(float amount, CallbackInfo callbackInfo) {
        if (aerogel$healOverride) return;
        EntityHealEvent event = new EntityHealEvent(EventHooks.cast(this), amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.amount(), amount) != 0) {
            aerogel$healOverride = true;
            try {
                EventHooks.call(this, "heal", event.amount());
            } finally {
                aerogel$healOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;"
        + "Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$effectAdded(
        @Coerce Object effect, @Coerce Object source, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$effectAddOverride) return;
        EntityEffectAddEvent event = new EntityEffectAddEvent(
            EventHooks.cast(this), EventHooks.cast(effect), EventHooks.cast(source));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.effect() != effect || event.source() != source) {
            aerogel$effectAddOverride = true;
            try {
                callbackInfo.setReturnValue((Boolean) EventHooks.call(
                    this, "addEffect", event.effect(), event.source()));
            } finally {
                aerogel$effectAddOverride = false;
            }
        }
    }

    @Inject(method = "removeEffect(Lnet/minecraft/core/Holder;)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$effectRemoved(
        @Coerce Object effect, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$effectRemoveOverride) return;
        EntityEffectRemoveEvent event = new EntityEffectRemoveEvent(
            EventHooks.cast(this), EventHooks.cast(effect));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.effect() != effect) {
            aerogel$effectRemoveOverride = true;
            try {
                callbackInfo.setReturnValue((Boolean) EventHooks.call(
                    this, "removeEffect", event.effect()));
            } finally {
                aerogel$effectRemoveOverride = false;
            }
        }
    }

    @Inject(method = "setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;"
        + "Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$equipmentChanged(
        @Coerce Object slot, @Coerce Object item, CallbackInfo callbackInfo
    ) {
        if (aerogel$equipmentOverride) return;
        EntityEquipmentChangeEvent event = new EntityEquipmentChangeEvent(
            EventHooks.cast(this), EventHooks.cast(slot),
            EventHooks.cast(EventHooks.call(this, "getItemBySlot", slot)), EventHooks.cast(item));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.item() != item) {
            aerogel$equipmentOverride = true;
            try {
                EventHooks.call(this, "setItemSlot", slot, event.item());
            } finally {
                aerogel$equipmentOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
