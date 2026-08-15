package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntityCombustEvent;
import dev.aerogel.api.event.entity.EntityAirSupplyChangeEvent;
import dev.aerogel.api.event.entity.EntityCustomNameChangeEvent;
import dev.aerogel.api.event.entity.EntityDismountEvent;
import dev.aerogel.api.event.entity.EntityFreezeTicksChangeEvent;
import dev.aerogel.api.event.entity.EntityGravityChangeEvent;
import dev.aerogel.api.event.entity.EntityMountEvent;
import dev.aerogel.api.event.entity.EntityPoseChangeEvent;
import dev.aerogel.api.event.entity.EntityRemoveEvent;
import dev.aerogel.api.event.entity.EntitySilentChangeEvent;
import dev.aerogel.api.event.entity.EntityTeleportEvent;
import dev.aerogel.api.event.entity.EntityVisibilityChangeEvent;
import dev.aerogel.api.event.player.PlayerSneakChangeEvent;
import dev.aerogel.api.event.player.PlayerSwimChangeEvent;
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
    @Unique private boolean aerogel$airOverride;
    @Unique private boolean aerogel$freezeOverride;
    @Unique private boolean aerogel$poseOverride;
    @Unique private boolean aerogel$customNameOverride;
    @Unique private boolean aerogel$visibilityOverride;
    @Unique private boolean aerogel$gravityOverride;
    @Unique private boolean aerogel$silentOverride;
    @Unique private boolean aerogel$playerStateOverride;

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

    @Inject(method = "setAirSupply(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$airSupply(int airSupply, CallbackInfo callbackInfo) {
        if (aerogel$airOverride) return;
        int previous = ((Number) EventHooks.call(this, "getAirSupply")).intValue();
        if (previous == airSupply) return;
        EntityAirSupplyChangeEvent event = new EntityAirSupplyChangeEvent(
            EventHooks.cast(this), previous, airSupply);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.airSupply() != airSupply) {
            aerogel$airOverride = true;
            try {
                EventHooks.call(this, "setAirSupply", event.airSupply());
            } finally {
                aerogel$airOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setTicksFrozen(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$freezeTicks(int frozenTicks, CallbackInfo callbackInfo) {
        if (aerogel$freezeOverride) return;
        int previous = ((Number) EventHooks.call(this, "getTicksFrozen")).intValue();
        if (previous == frozenTicks) return;
        EntityFreezeTicksChangeEvent event = new EntityFreezeTicksChangeEvent(
            EventHooks.cast(this), previous, frozenTicks);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.frozenTicks() != frozenTicks) {
            aerogel$freezeOverride = true;
            try {
                EventHooks.call(this, "setTicksFrozen", event.frozenTicks());
            } finally {
                aerogel$freezeOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setPose(Lnet/minecraft/world/entity/Pose;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$pose(@Coerce Object pose, CallbackInfo callbackInfo) {
        if (aerogel$poseOverride) return;
        Object previous = EventHooks.call(this, "getPose");
        if (previous == pose) return;
        EntityPoseChangeEvent event = new EntityPoseChangeEvent(
            EventHooks.cast(this), EventHooks.cast(previous), EventHooks.cast(pose));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.pose() != pose) {
            aerogel$poseOverride = true;
            try {
                EventHooks.call(this, "setPose", event.pose());
            } finally {
                aerogel$poseOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setCustomName(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$customName(@Coerce Object customName, CallbackInfo callbackInfo) {
        if (aerogel$customNameOverride) return;
        Object previous = EventHooks.call(this, "getCustomName");
        if (Objects.equals(previous, customName)) return;
        EntityCustomNameChangeEvent event = new EntityCustomNameChangeEvent(
            EventHooks.cast(this), EventHooks.cast(previous), EventHooks.cast(customName));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (!Objects.equals(event.customName(), customName)) {
            aerogel$customNameOverride = true;
            try {
                EventHooks.call(this, "setCustomName", event.customName());
            } finally {
                aerogel$customNameOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setInvisible(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$visibility(boolean invisible, CallbackInfo callbackInfo) {
        if (aerogel$visibilityOverride) return;
        boolean previous = (Boolean) EventHooks.call(this, "isInvisible");
        if (previous == invisible) return;
        EntityVisibilityChangeEvent event = new EntityVisibilityChangeEvent(
            EventHooks.cast(this), previous, invisible);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.invisible() != invisible) {
            aerogel$visibilityOverride = true;
            try {
                EventHooks.call(this, "setInvisible", event.invisible());
            } finally {
                aerogel$visibilityOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setNoGravity(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$gravity(boolean noGravity, CallbackInfo callbackInfo) {
        if (aerogel$gravityOverride) return;
        boolean previous = (Boolean) EventHooks.call(this, "isNoGravity");
        if (previous == noGravity) return;
        EntityGravityChangeEvent event = new EntityGravityChangeEvent(
            EventHooks.cast(this), previous, noGravity);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.noGravity() != noGravity) {
            aerogel$gravityOverride = true;
            try {
                EventHooks.call(this, "setNoGravity", event.noGravity());
            } finally {
                aerogel$gravityOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setSilent(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$silent(boolean silent, CallbackInfo callbackInfo) {
        if (aerogel$silentOverride) return;
        boolean previous = (Boolean) EventHooks.call(this, "isSilent");
        if (previous == silent) return;
        EntitySilentChangeEvent event = new EntitySilentChangeEvent(
            EventHooks.cast(this), previous, silent);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.silent() != silent) {
            aerogel$silentOverride = true;
            try {
                EventHooks.call(this, "setSilent", event.silent());
            } finally {
                aerogel$silentOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setShiftKeyDown(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$sneaking(boolean sneaking, CallbackInfo callbackInfo) {
        if (aerogel$playerStateOverride
            || !EventHooks.isInstance(this, "net.minecraft.server.level.ServerPlayer")) return;
        boolean previous = (Boolean) EventHooks.call(this, "isShiftKeyDown");
        if (previous == sneaking) return;
        PlayerSneakChangeEvent event = new PlayerSneakChangeEvent(
            EventHooks.cast(this), previous, sneaking);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.sneaking() != sneaking) {
            aerogel$playerStateOverride = true;
            try {
                EventHooks.call(this, "setShiftKeyDown", event.sneaking());
            } finally {
                aerogel$playerStateOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setSwimming(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$swimming(boolean swimming, CallbackInfo callbackInfo) {
        if (aerogel$playerStateOverride
            || !EventHooks.isInstance(this, "net.minecraft.server.level.ServerPlayer")) return;
        boolean previous = (Boolean) EventHooks.call(this, "isSwimming");
        if (previous == swimming) return;
        PlayerSwimChangeEvent event = new PlayerSwimChangeEvent(
            EventHooks.cast(this), previous, swimming);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.swimming() != swimming) {
            aerogel$playerStateOverride = true;
            try {
                EventHooks.call(this, "setSwimming", event.swimming());
            } finally {
                aerogel$playerStateOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
