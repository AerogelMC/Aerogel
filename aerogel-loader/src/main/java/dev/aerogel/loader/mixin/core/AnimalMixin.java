package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityBreedEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.animal.Animal")
abstract class AnimalMixin {
    @Inject(method = "spawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/entity/animal/Animal;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$breed(
        @Coerce Object level, @Coerce Object partner, CallbackInfo callbackInfo
    ) {
        EntityBreedEvent event = new EntityBreedEvent(
            EventHooks.cast(level), EventHooks.cast(this), EventHooks.cast(partner));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }
}
