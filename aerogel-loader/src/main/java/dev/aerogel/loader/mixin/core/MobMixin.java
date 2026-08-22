package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityTargetEvent;
import dev.aerogel.loader.event.EventHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import dev.aerogel.loader.internal.NavigationIndexBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.Mob")
abstract class MobMixin {
    @Unique private boolean aerogel$targetOverride;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void aerogel$beginNavigationIndexUpdate(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (mob.level() instanceof ServerLevel level) {
            ((NavigationIndexBridge) level).aerogel$beginNavigationUpdate(mob);
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void aerogel$finishNavigationIndexUpdate(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (mob.level() instanceof ServerLevel level) {
            ((NavigationIndexBridge) level).aerogel$finishNavigationUpdate(mob);
        }
    }

    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$target(LivingEntity target, CallbackInfo callbackInfo) {
        if (aerogel$targetOverride || !EventHooks.hasListeners(EntityTargetEvent.class)) return;
        Mob self = (Mob) (Object) this;
        LivingEntity previous = self.getTarget();
        if (previous == target) return;
        EntityTargetEvent event = new EntityTargetEvent(
            self, previous, target);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.target() != target) {
            aerogel$targetOverride = true;
            try {
                self.setTarget(event.target());
            } finally {
                aerogel$targetOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
