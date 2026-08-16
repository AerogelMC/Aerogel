package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityBreedEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.animal.Animal")
abstract class AnimalMixin {
    @Unique private boolean aerogel$breedOverride;

    @Inject(method = "spawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/entity/animal/Animal;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$breed(
        ServerLevel level, Animal partner, CallbackInfo callbackInfo
    ) {
        if (aerogel$breedOverride || !EventHooks.hasListeners(EntityBreedEvent.class)) return;
        Animal self = (Animal) (Object) this;
        EntityBreedEvent event = new EntityBreedEvent(level, self, partner);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.partner() != partner) {
            aerogel$breedOverride = true;
            try {
                self.spawnChildFromBreeding(level, event.partner());
            } finally {
                aerogel$breedOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
