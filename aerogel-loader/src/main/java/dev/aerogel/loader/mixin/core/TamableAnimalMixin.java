package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityTameEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
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
    private void aerogel$tame(Player player, CallbackInfo callbackInfo) {
        if (aerogel$tameOverride || !EventHooks.hasListeners(EntityTameEvent.class)) return;
        TamableAnimal self = (TamableAnimal) (Object) this;
        EntityTameEvent event = new EntityTameEvent(self, player);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.player() != player) {
            aerogel$tameOverride = true;
            try {
                self.tame(event.player());
            } finally {
                aerogel$tameOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
