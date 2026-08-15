package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityDamageEvent;
import dev.aerogel.api.event.entity.EntityAbsorptionChangeEvent;
import dev.aerogel.api.event.entity.EntityEffectAddEvent;
import dev.aerogel.api.event.entity.EntityEffectRemoveEvent;
import dev.aerogel.api.event.entity.EntityEquipmentChangeEvent;
import dev.aerogel.api.event.entity.EntityHealEvent;
import dev.aerogel.api.event.entity.EntityHealthChangeEvent;
import dev.aerogel.api.event.entity.EntityJumpEvent;
import dev.aerogel.api.event.entity.EntityKnockbackEvent;
import dev.aerogel.api.event.entity.EntityRandomTeleportEvent;
import dev.aerogel.api.event.player.PlayerItemUseEndEvent;
import dev.aerogel.api.event.player.PlayerItemUseStartEvent;
import dev.aerogel.api.event.player.PlayerSprintChangeEvent;
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
    @Unique private boolean aerogel$healthOverride;
    @Unique private boolean aerogel$absorptionOverride;
    @Unique private boolean aerogel$knockbackOverride;
    @Unique private boolean aerogel$randomTeleportOverride;
    @Unique private boolean aerogel$sprintOverride;
    @Unique private PlayerItemUseEndEvent.Reason aerogel$itemUseEndReason;

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

    @Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;getUseDuration("
                + "Lnet/minecraft/world/entity/LivingEntity;)I"),
        cancellable = true)
    private void aerogel$itemUseStarted(
        @Coerce Object hand, CallbackInfo callbackInfo
    ) {
        if (!aerogel$isServerPlayer()) return;
        Object item = EventHooks.call(this, "getItemInHand", hand);
        PlayerItemUseStartEvent event = new PlayerItemUseStartEvent(
            EventHooks.cast(this), EventHooks.cast(hand), EventHooks.cast(item));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "releaseUsingItem()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$itemUseReleased(CallbackInfo callbackInfo) {
        if (!aerogel$isServerPlayer() || !(Boolean) EventHooks.call(this, "isUsingItem")) return;
        PlayerItemUseEndEvent event = aerogel$itemUseEndEvent(
            PlayerItemUseEndEvent.Reason.RELEASED);
        EventHooks.post(event);
        EventHooks.setField(this, "useItemRemaining", event.remainingTicks());
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else {
            aerogel$itemUseEndReason = PlayerItemUseEndEvent.Reason.RELEASED;
        }
    }

    @Inject(method = "releaseUsingItem()V", at = @At("RETURN"))
    private void aerogel$itemUseReleaseCompleted(CallbackInfo callbackInfo) {
        if (aerogel$itemUseEndReason == PlayerItemUseEndEvent.Reason.RELEASED) {
            aerogel$itemUseEndReason = null;
        }
    }

    @Inject(
        method = "completeUsingItem()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem("
                + "Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/world/entity/LivingEntity;)"
                + "Lnet/minecraft/world/item/ItemStack;"
        ),
        cancellable = true
    )
    private void aerogel$itemUseCompleted(CallbackInfo callbackInfo) {
        if (!aerogel$isServerPlayer() || !(Boolean) EventHooks.call(this, "isUsingItem")) return;
        PlayerItemUseEndEvent event = aerogel$itemUseEndEvent(
            PlayerItemUseEndEvent.Reason.COMPLETED);
        EventHooks.post(event);
        EventHooks.setField(this, "useItemRemaining", event.remainingTicks());
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else {
            aerogel$itemUseEndReason = PlayerItemUseEndEvent.Reason.COMPLETED;
        }
    }

    @Inject(method = "completeUsingItem()V", at = @At("RETURN"))
    private void aerogel$itemUseCompletionFinished(CallbackInfo callbackInfo) {
        if (aerogel$itemUseEndReason == PlayerItemUseEndEvent.Reason.COMPLETED) {
            aerogel$itemUseEndReason = null;
        }
    }

    @Inject(method = "stopUsingItem()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$itemUseInterrupted(CallbackInfo callbackInfo) {
        if (aerogel$itemUseEndReason != null
            || !aerogel$isServerPlayer()
            || !(Boolean) EventHooks.call(this, "isUsingItem")) return;
        PlayerItemUseEndEvent event = aerogel$itemUseEndEvent(
            PlayerItemUseEndEvent.Reason.INTERRUPTED);
        EventHooks.post(event);
        EventHooks.setField(this, "useItemRemaining", event.remainingTicks());
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "setHealth(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$health(float health, CallbackInfo callbackInfo) {
        if (aerogel$healthOverride) return;
        float previous = ((Number) EventHooks.call(this, "getHealth")).floatValue();
        if (Float.compare(previous, health) == 0) return;
        EntityHealthChangeEvent event = new EntityHealthChangeEvent(
            EventHooks.cast(this), previous, health);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.health(), health) != 0) {
            aerogel$healthOverride = true;
            try {
                EventHooks.call(this, "setHealth", event.health());
            } finally {
                aerogel$healthOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setAbsorptionAmount(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$absorption(float absorption, CallbackInfo callbackInfo) {
        if (aerogel$absorptionOverride) return;
        float previous = ((Number) EventHooks.call(this, "getAbsorptionAmount")).floatValue();
        if (Float.compare(previous, absorption) == 0) return;
        EntityAbsorptionChangeEvent event = new EntityAbsorptionChangeEvent(
            EventHooks.cast(this), previous, absorption);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.absorption(), absorption) != 0) {
            aerogel$absorptionOverride = true;
            try {
                EventHooks.call(this, "setAbsorptionAmount", event.absorption());
            } finally {
                aerogel$absorptionOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$knockback(
        double strength,
        double directionX,
        double directionZ,
        @Coerce Object damageSource,
        float verticalStrength,
        boolean limitVertical,
        CallbackInfo callbackInfo
    ) {
        if (aerogel$knockbackOverride) return;
        EntityKnockbackEvent event = new EntityKnockbackEvent(
            EventHooks.cast(this), strength, directionX, directionZ,
            EventHooks.cast(damageSource), verticalStrength, limitVertical);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Double.compare(event.strength(), strength) != 0
            || Double.compare(event.directionX(), directionX) != 0
            || Double.compare(event.directionZ(), directionZ) != 0
            || event.damageSource() != damageSource
            || Float.compare(event.verticalStrength(), verticalStrength) != 0
            || event.limitVertical() != limitVertical) {
            aerogel$knockbackOverride = true;
            try {
                EventHooks.call(this, "knockback", event.strength(), event.directionX(),
                    event.directionZ(), event.damageSource(), event.verticalStrength(),
                    event.limitVertical());
            } finally {
                aerogel$knockbackOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "jumpFromGround()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$jump(CallbackInfo callbackInfo) {
        EntityJumpEvent event = new EntityJumpEvent(EventHooks.cast(this));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "randomTeleport(DDDZ)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$randomTeleport(
        double x,
        double y,
        double z,
        boolean showParticles,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$randomTeleportOverride) return;
        EntityRandomTeleportEvent event = new EntityRandomTeleportEvent(
            EventHooks.cast(this), x, y, z, showParticles);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (Double.compare(event.x(), x) != 0
            || Double.compare(event.y(), y) != 0
            || Double.compare(event.z(), z) != 0
            || event.showParticles() != showParticles) {
            aerogel$randomTeleportOverride = true;
            try {
                callbackInfo.setReturnValue((Boolean) EventHooks.call(this, "randomTeleport",
                    event.x(), event.y(), event.z(), event.showParticles()));
            } finally {
                aerogel$randomTeleportOverride = false;
            }
        }
    }

    @Inject(method = "setSprinting(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$sprinting(boolean sprinting, CallbackInfo callbackInfo) {
        if (aerogel$sprintOverride || !aerogel$isServerPlayer()) return;
        boolean previous = (Boolean) EventHooks.call(this, "isSprinting");
        if (previous == sprinting) return;
        PlayerSprintChangeEvent event = new PlayerSprintChangeEvent(
            EventHooks.cast(this), previous, sprinting);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.sprinting() != sprinting) {
            aerogel$sprintOverride = true;
            try {
                EventHooks.call(this, "setSprinting", event.sprinting());
            } finally {
                aerogel$sprintOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Unique
    private boolean aerogel$isServerPlayer() {
        return EventHooks.isInstance(this, "net.minecraft.server.level.ServerPlayer");
    }

    @Unique
    private PlayerItemUseEndEvent aerogel$itemUseEndEvent(
        PlayerItemUseEndEvent.Reason reason
    ) {
        return new PlayerItemUseEndEvent(
            EventHooks.cast(this),
            EventHooks.cast(EventHooks.call(this, "getUsedItemHand")),
            EventHooks.cast(EventHooks.call(this, "getUseItem")),
            reason,
            ((Number) EventHooks.call(this, "getUseItemRemainingTicks")).intValue());
    }
}
