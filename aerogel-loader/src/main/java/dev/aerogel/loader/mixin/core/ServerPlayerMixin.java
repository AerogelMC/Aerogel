package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.inventory.InventoryCloseEvent;
import dev.aerogel.api.event.inventory.InventoryOpenEvent;
import dev.aerogel.api.event.item.PlayerDropItemEvent;
import dev.aerogel.api.event.player.PlayerDeathEvent;
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
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$openInventory(
        @Coerce Object provider, CallbackInfoReturnable<OptionalInt> callbackInfo
    ) {
        InventoryOpenEvent event = new InventoryOpenEvent(this, provider);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(OptionalInt.empty());
        }
    }

    @Inject(method = "closeContainer()V", at = @At("HEAD"))
    private void aerogel$closeInventory(CallbackInfo callbackInfo) {
        EventHooks.post(new InventoryCloseEvent(this));
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)"
        + "Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$dropItem(
        @Coerce Object itemStack, boolean randomThrow, boolean retainOwnership,
        CallbackInfoReturnable<Object> callbackInfo
    ) {
        PlayerDropItemEvent event = new PlayerDropItemEvent(this, itemStack, randomThrow, retainOwnership);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(null);
        }
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void aerogel$playerDeath(@Coerce Object source, CallbackInfo callbackInfo) {
        EventHooks.post(new PlayerDeathEvent(this, source));
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDD"
        + "Ljava/util/Set;FFZ)Z", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        @Coerce Object level, double x, double y, double z, @Coerce Object relative,
        float yaw, float pitch, boolean dismount,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(this, level, x, y, z, yaw, pitch);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
