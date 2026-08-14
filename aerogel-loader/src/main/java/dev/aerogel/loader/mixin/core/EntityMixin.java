package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityCombustEvent;
import dev.aerogel.api.event.entity.EntityDismountEvent;
import dev.aerogel.api.event.entity.EntityMountEvent;
import dev.aerogel.api.event.entity.EntityRemoveEvent;
import dev.aerogel.api.event.entity.EntityTeleportEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.world.entity.Entity")
abstract class EntityMixin {
    @Unique private boolean aerogel$combustOverride;
    @Unique private boolean aerogel$teleportOverride;
    @Unique private boolean aerogel$mountOverride;

    @Unique
    public Collection<Entity> nearbyEntities(double radius) {
        return nearbyEntities(radius, entity -> true);
    }

    @Unique
    public Collection<Entity> nearbyEntities(double radius, Predicate<Entity> filter) {
        Object level = EventHooks.call(this, "level");
        if (!EventHooks.isInstance(level, "net.minecraft.server.level.ServerLevel")) return List.of();
        Collection<Entity> nearby = ((ServerLevel) level).nearbyEntities(
            ((Number) EventHooks.call(this, "getX")).doubleValue(),
            ((Number) EventHooks.call(this, "getY")).doubleValue(),
            ((Number) EventHooks.call(this, "getZ")).doubleValue(), radius,
            Objects.requireNonNull(filter, "filter"));
        Object self = this;
        return nearby.stream().filter(entity -> entity != self).toList();
    }

    @Unique
    public boolean teleport(ServerLevel destination, double x, double y, double z) {
        return teleport(destination, x, y, z,
            ((Number) EventHooks.call(this, "getYRot")).floatValue(),
            ((Number) EventHooks.call(this, "getXRot")).floatValue());
    }

    @Unique
    public boolean teleport(
        ServerLevel destination, double x, double y, double z, float yaw, float pitch
    ) {
        Objects.requireNonNull(destination, "destination");
        return (boolean) EventHooks.call(this, "teleportTo", destination,
            x, y, z, Set.of(), yaw, pitch, true);
    }

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
        if (aerogel$mountOverride) return;
        EntityMountEvent event = new EntityMountEvent(
            EventHooks.cast(this), EventHooks.cast(vehicle), force);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.vehicle() != vehicle || event.force() != force) {
            aerogel$mountOverride = true;
            try {
                callbackInfo.setReturnValue((Boolean) EventHooks.call(this, "startRiding",
                    event.vehicle(), event.force(), teleport));
            } finally {
                aerogel$mountOverride = false;
            }
        }
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
        if (aerogel$combustOverride) return;
        EntityCombustEvent event = new EntityCombustEvent(EventHooks.cast(this), durationTicks);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.durationTicks() != durationTicks) {
            aerogel$combustOverride = true;
            try {
                EventHooks.call(this, "igniteForTicks", event.durationTicks());
            } finally {
                aerogel$combustOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)"
        + "Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        @Coerce Object transition, CallbackInfoReturnable<Object> callbackInfo
    ) {
        if (aerogel$teleportOverride) return;
        EntityTeleportEvent event = new EntityTeleportEvent(
            EventHooks.cast(this), EventHooks.cast(transition));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(null);
        } else if (event.transition() != transition) {
            aerogel$teleportOverride = true;
            try {
                callbackInfo.setReturnValue(EventHooks.call(this, "teleport", event.transition()));
            } finally {
                aerogel$teleportOverride = false;
            }
        }
    }
}
