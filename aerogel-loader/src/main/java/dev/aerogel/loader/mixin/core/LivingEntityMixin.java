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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.LivingEntity")
abstract class LivingEntityMixin {
    @Shadow private int useItemRemaining;
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
        ServerLevel level,
        DamageSource source,
        float amount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$damageOverride || !EventHooks.hasListeners(EntityDamageEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityDamageEvent event = new EntityDamageEvent(
            self, level, source, amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.damageSource() != source || Float.compare(event.amount(), amount) != 0) {
            aerogel$damageOverride = true;
            try {
                callbackInfo.setReturnValue(self.hurtServer(
                    level, event.damageSource(), event.amount()));
            } finally {
                aerogel$damageOverride = false;
            }
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void aerogel$beginDeathDrops(
        ServerLevel level, DamageSource source, CallbackInfo callbackInfo
    ) {
        DeathDropCapture.begin((LivingEntity) (Object) this, source);
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
        ServerLevel level, DamageSource source, CallbackInfo callbackInfo
    ) {
        DeathDropCapture.complete(level, (LivingEntity) (Object) this, source);
    }

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$heal(float amount, CallbackInfo callbackInfo) {
        if (aerogel$healOverride || !EventHooks.hasListeners(EntityHealEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityHealEvent event = new EntityHealEvent(self, amount);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.amount(), amount) != 0) {
            aerogel$healOverride = true;
            try {
                self.heal(event.amount());
            } finally {
                aerogel$healOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;"
        + "Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$effectAdded(
        MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$effectAddOverride || !EventHooks.hasListeners(EntityEffectAddEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityEffectAddEvent event = new EntityEffectAddEvent(self, effect, source);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.effect() != effect || event.source() != source) {
            aerogel$effectAddOverride = true;
            try {
                callbackInfo.setReturnValue(self.addEffect(event.effect(), event.source()));
            } finally {
                aerogel$effectAddOverride = false;
            }
        }
    }

    @Inject(method = "removeEffect(Lnet/minecraft/core/Holder;)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$effectRemoved(
        Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$effectRemoveOverride || !EventHooks.hasListeners(EntityEffectRemoveEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityEffectRemoveEvent event = new EntityEffectRemoveEvent(self, effect);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.effect() != effect) {
            aerogel$effectRemoveOverride = true;
            try {
                callbackInfo.setReturnValue(self.removeEffect(event.effect()));
            } finally {
                aerogel$effectRemoveOverride = false;
            }
        }
    }

    @Inject(method = "setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;"
        + "Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$equipmentChanged(
        EquipmentSlot slot, ItemStack item, CallbackInfo callbackInfo
    ) {
        if (aerogel$equipmentOverride || !EventHooks.hasListeners(EntityEquipmentChangeEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityEquipmentChangeEvent event = new EntityEquipmentChangeEvent(
            self, slot, self.getItemBySlot(slot), item);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.item() != item) {
            aerogel$equipmentOverride = true;
            try {
                self.setItemSlot(slot, event.item());
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
        InteractionHand hand, CallbackInfo callbackInfo
    ) {
        if (!EventHooks.hasListeners(PlayerItemUseStartEvent.class)
            || !((Object) this instanceof ServerPlayer player)) return;
        ItemStack item = player.getItemInHand(hand);
        PlayerItemUseStartEvent event = new PlayerItemUseStartEvent(
            player, hand, item);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "releaseUsingItem()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$itemUseReleased(CallbackInfo callbackInfo) {
        if (!EventHooks.hasListeners(PlayerItemUseEndEvent.class)
            || !aerogel$isServerPlayer() || !((LivingEntity) (Object) this).isUsingItem()) return;
        PlayerItemUseEndEvent event = aerogel$itemUseEndEvent(
            PlayerItemUseEndEvent.Reason.RELEASED);
        EventHooks.post(event);
        useItemRemaining = event.remainingTicks();
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
        if (!EventHooks.hasListeners(PlayerItemUseEndEvent.class)
            || !aerogel$isServerPlayer() || !((LivingEntity) (Object) this).isUsingItem()) return;
        PlayerItemUseEndEvent event = aerogel$itemUseEndEvent(
            PlayerItemUseEndEvent.Reason.COMPLETED);
        EventHooks.post(event);
        useItemRemaining = event.remainingTicks();
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
            || !EventHooks.hasListeners(PlayerItemUseEndEvent.class)
            || !aerogel$isServerPlayer()
            || !((LivingEntity) (Object) this).isUsingItem()) return;
        PlayerItemUseEndEvent event = aerogel$itemUseEndEvent(
            PlayerItemUseEndEvent.Reason.INTERRUPTED);
        EventHooks.post(event);
        useItemRemaining = event.remainingTicks();
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "setHealth(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$health(float health, CallbackInfo callbackInfo) {
        if (aerogel$healthOverride || !EventHooks.hasListeners(EntityHealthChangeEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        float previous = self.getHealth();
        if (Float.compare(previous, health) == 0) return;
        EntityHealthChangeEvent event = new EntityHealthChangeEvent(
            self, previous, health);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.health(), health) != 0) {
            aerogel$healthOverride = true;
            try {
                self.setHealth(event.health());
            } finally {
                aerogel$healthOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setAbsorptionAmount(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$absorption(float absorption, CallbackInfo callbackInfo) {
        if (aerogel$absorptionOverride || !EventHooks.hasListeners(EntityAbsorptionChangeEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        float previous = self.getAbsorptionAmount();
        if (Float.compare(previous, absorption) == 0) return;
        EntityAbsorptionChangeEvent event = new EntityAbsorptionChangeEvent(
            self, previous, absorption);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Float.compare(event.absorption(), absorption) != 0) {
            aerogel$absorptionOverride = true;
            try {
                self.setAbsorptionAmount(event.absorption());
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
        DamageSource damageSource,
        float verticalStrength,
        boolean limitVertical,
        CallbackInfo callbackInfo
    ) {
        if (aerogel$knockbackOverride || !EventHooks.hasListeners(EntityKnockbackEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityKnockbackEvent event = new EntityKnockbackEvent(
            self, strength, directionX, directionZ,
            damageSource, verticalStrength, limitVertical);
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
                self.knockback(event.strength(), event.directionX(),
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
        if (!EventHooks.hasListeners(EntityJumpEvent.class)) return;
        EntityJumpEvent event = new EntityJumpEvent((LivingEntity) (Object) this);
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
        if (aerogel$randomTeleportOverride || !EventHooks.hasListeners(EntityRandomTeleportEvent.class)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        EntityRandomTeleportEvent event = new EntityRandomTeleportEvent(
            self, x, y, z, showParticles);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (Double.compare(event.x(), x) != 0
            || Double.compare(event.y(), y) != 0
            || Double.compare(event.z(), z) != 0
            || event.showParticles() != showParticles) {
            aerogel$randomTeleportOverride = true;
            try {
                callbackInfo.setReturnValue(self.randomTeleport(
                    event.x(), event.y(), event.z(), event.showParticles()));
            } finally {
                aerogel$randomTeleportOverride = false;
            }
        }
    }

    @Inject(method = "setSprinting(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$sprinting(boolean sprinting, CallbackInfo callbackInfo) {
        if (aerogel$sprintOverride || !EventHooks.hasListeners(PlayerSprintChangeEvent.class)
            || !((Object) this instanceof ServerPlayer player)) return;
        boolean previous = player.isSprinting();
        if (previous == sprinting) return;
        PlayerSprintChangeEvent event = new PlayerSprintChangeEvent(
            player, previous, sprinting);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.sprinting() != sprinting) {
            aerogel$sprintOverride = true;
            try {
                player.setSprinting(event.sprinting());
            } finally {
                aerogel$sprintOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Unique
    private boolean aerogel$isServerPlayer() {
        return (Object) this instanceof ServerPlayer;
    }

    @Unique
    private PlayerItemUseEndEvent aerogel$itemUseEndEvent(
        PlayerItemUseEndEvent.Reason reason
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        return new PlayerItemUseEndEvent(
            (ServerPlayer) (Object) this, self.getUsedItemHand(), self.getUseItem(),
            reason, self.getUseItemRemainingTicks());
    }
}
