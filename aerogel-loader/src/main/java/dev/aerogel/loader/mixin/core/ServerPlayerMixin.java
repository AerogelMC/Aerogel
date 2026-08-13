package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.inventory.InventoryCloseEvent;
import dev.aerogel.api.event.inventory.InventoryOpenEvent;
import dev.aerogel.api.event.item.PlayerDropItemEvent;
import dev.aerogel.api.event.player.PlayerDeathEvent;
import dev.aerogel.api.event.player.PlayerBedEnterEvent;
import dev.aerogel.api.event.player.PlayerBedLeaveEvent;
import dev.aerogel.api.event.player.PlayerExperienceChangeEvent;
import dev.aerogel.api.event.player.PlayerItemConsumeEvent;
import dev.aerogel.api.event.player.PlayerTeleportEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(targets = "net.minecraft.server.level.ServerPlayer")
abstract class ServerPlayerMixin {
    @Inject(method = "startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$bedEnter(
        @Coerce Object position, CallbackInfoReturnable<Object> callbackInfo
    ) {
        PlayerBedEnterEvent event = new PlayerBedEnterEvent(
            EventHooks.cast(this), EventHooks.cast(position));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(EventHooks.eitherLeft(
                this, "net.minecraft.world.entity.player.Player$BedSleepingProblem", "OTHER_PROBLEM"));
        }
    }

    @Inject(method = "stopSleepInBed(ZZ)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$bedLeave(
        boolean resetSleepTimer, boolean updateSleepingPlayers, CallbackInfo callbackInfo
    ) {
        PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(
            EventHooks.cast(this), resetSleepTimer, updateSleepingPlayers);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "giveExperiencePoints(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$experiencePoints(int amount, CallbackInfo callbackInfo) {
        PlayerExperienceChangeEvent event = new PlayerExperienceChangeEvent(
            EventHooks.cast(this), amount, PlayerExperienceChangeEvent.Unit.POINTS);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "giveExperienceLevels(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$experienceLevels(int amount, CallbackInfo callbackInfo) {
        PlayerExperienceChangeEvent event = new PlayerExperienceChangeEvent(
            EventHooks.cast(this), amount, PlayerExperienceChangeEvent.Unit.LEVELS);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "completeUsingItem()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$itemConsume(CallbackInfo callbackInfo) {
        PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(
            EventHooks.cast(this), EventHooks.cast(EventHooks.call(this, "getUsedItemHand")),
            EventHooks.cast(EventHooks.call(this, "getUseItem")));
        EventHooks.post(event);
        if (event.isCancelled()) {
            EventHooks.call(this, "stopUsingItem");
            callbackInfo.cancel();
        }
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$openInventory(
        @Coerce Object provider, CallbackInfoReturnable<OptionalInt> callbackInfo
    ) {
        InventoryOpenEvent event = new InventoryOpenEvent(
            EventHooks.cast(this), EventHooks.cast(provider));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(OptionalInt.empty());
        }
    }

    @Inject(method = "closeContainer()V", at = @At("HEAD"))
    private void aerogel$closeInventory(CallbackInfo callbackInfo) {
        EventHooks.post(new InventoryCloseEvent(EventHooks.cast(this)));
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)"
        + "Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$dropItem(
        @Coerce Object itemStack, boolean randomThrow, boolean retainOwnership,
        CallbackInfoReturnable<Object> callbackInfo
    ) {
        PlayerDropItemEvent event = new PlayerDropItemEvent(
            EventHooks.cast(this), EventHooks.cast(itemStack), randomThrow, retainOwnership);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(null);
        }
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void aerogel$playerDeath(@Coerce Object source, CallbackInfo callbackInfo) {
        EventHooks.post(new PlayerDeathEvent(EventHooks.cast(this), EventHooks.cast(source)));
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDD"
        + "Ljava/util/Set;FFZ)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        @Coerce Object level, double x, double y, double z, @Coerce Object relative,
        float yaw, float pitch, boolean dismount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(
            EventHooks.cast(this), EventHooks.cast(level), x, y, z, yaw, pitch);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
