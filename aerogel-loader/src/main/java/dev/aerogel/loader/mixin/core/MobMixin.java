package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityTargetEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.Mob")
abstract class MobMixin {
    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$target(@Coerce Object target, CallbackInfo callbackInfo) {
        Object previous = EventHooks.call(this, "getTarget");
        if (previous == target) return;
        EntityTargetEvent event = new EntityTargetEvent(
            EventHooks.cast(this), EventHooks.cast(previous), EventHooks.cast(target));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }
}
