package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityCombustEvent;
import dev.aerogel.api.event.entity.EntityDismountEvent;
import dev.aerogel.api.event.entity.EntityMountEvent;
import dev.aerogel.api.event.entity.EntityRemoveEvent;
import dev.aerogel.api.event.entity.EntityTeleportEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.Entity")
abstract class EntityMixin {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void aerogel$removed(@Coerce Object reason, CallbackInfo callbackInfo) {
        EventHooks.post(new EntityRemoveEvent(EventHooks.cast(this), EventHooks.cast(reason)));
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$mount(
        @Coerce Object vehicle, boolean force, boolean teleport,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EntityMountEvent event = new EntityMountEvent(
            EventHooks.cast(this), EventHooks.cast(vehicle), force);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.setReturnValue(false);
    }

    @Inject(method = "stopRiding()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$dismount(CallbackInfo callbackInfo) {
        Object vehicle = EventHooks.call(this, "getVehicle");
        if (vehicle != null) {
            EntityDismountEvent event = new EntityDismountEvent(
                EventHooks.cast(this), EventHooks.cast(vehicle));
            EventHooks.post(event);
            if (event.isCancelled()) callbackInfo.cancel();
        }
    }

    @Inject(method = "igniteForTicks(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$combust(int durationTicks, CallbackInfo callbackInfo) {
        EntityCombustEvent event = new EntityCombustEvent(EventHooks.cast(this), durationTicks);
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)"
        + "Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        @Coerce Object transition, CallbackInfoReturnable<Object> callbackInfo
    ) {
        EntityTeleportEvent event = new EntityTeleportEvent(
            EventHooks.cast(this), EventHooks.cast(transition));
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.setReturnValue(null);
    }
}
