package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.player.PlayerFoodExhaustionEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.player.Player")
abstract class PlayerMixin {
    @Inject(method = "causeFoodExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$foodExhaustion(float amount, CallbackInfo callbackInfo) {
        PlayerFoodExhaustionEvent event = new PlayerFoodExhaustionEvent(
            EventHooks.cast(this), amount);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }
}
