package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityRemoveEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.Entity")
abstract class EntityMixin {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void aerogel$removed(@Coerce Object reason, CallbackInfo callbackInfo) {
        EventHooks.post(new EntityRemoveEvent(this, reason));
    }
}
