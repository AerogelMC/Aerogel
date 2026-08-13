package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.ProjectileHitEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.projectile.Projectile")
abstract class ProjectileMixin {
    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$hit(@Coerce Object hitResult, CallbackInfo callbackInfo) {
        ProjectileHitEvent event = new ProjectileHitEvent(
            EventHooks.cast(this), EventHooks.cast(hitResult));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }
}
