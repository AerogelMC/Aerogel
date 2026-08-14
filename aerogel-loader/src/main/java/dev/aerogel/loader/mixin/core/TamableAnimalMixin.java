package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityTameEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.TamableAnimal")
abstract class TamableAnimalMixin {
    @Unique private boolean aerogel$tameOverride;

    @Inject(method = "tame(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$tame(@Coerce Object player, CallbackInfo callbackInfo) {
        if (aerogel$tameOverride) return;
        EntityTameEvent event = new EntityTameEvent(
            EventHooks.cast(this), EventHooks.cast(player));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.player() != player) {
            aerogel$tameOverride = true;
            try {
                EventHooks.call(this, "tame", event.player());
            } finally {
                aerogel$tameOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
