package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.item.PlayerPickupItemEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.item.ItemEntity")
abstract class ItemEntityMixin {
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$pickup(@Coerce Object player, CallbackInfo callbackInfo) {
        PlayerPickupItemEvent event = new PlayerPickupItemEvent(
            EventHooks.cast(player), EventHooks.cast(this));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
