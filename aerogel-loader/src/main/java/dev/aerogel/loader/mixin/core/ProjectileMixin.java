package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.ProjectileHitEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.projectile.Projectile")
abstract class ProjectileMixin {
    @Unique private boolean aerogel$hitOverride;

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$hit(HitResult hitResult, CallbackInfo callbackInfo) {
        if (aerogel$hitOverride || !EventHooks.hasListeners(ProjectileHitEvent.class)) return;
        Projectile self = (Projectile) (Object) this;
        ProjectileHitEvent event = new ProjectileHitEvent(self, hitResult);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.hitResult() != hitResult) {
            aerogel$hitOverride = true;
            try {
                self.onHit(event.hitResult());
            } finally {
                aerogel$hitOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
