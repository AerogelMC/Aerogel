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
import dev.aerogel.loader.internal.EntityViewBridge;
import dev.aerogel.loader.internal.PersistentDataHolderBridge;
import dev.aerogel.loader.internal.PersistentDataViews;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.api.persistence.PersistentDataView;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.virtualentity.VirtualEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.world.entity.Entity")
abstract class EntityMixin implements EntityViewBridge, PersistentDataHolderBridge {
    @Shadow protected static EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID;
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
    @Unique private CompoundTag aerogel$persistentData = new CompoundTag();
    @Unique private PersistentDataView aerogel$dataView;

    @Unique
    public synchronized PersistentDataView data() {
        if (aerogel$dataView == null) {
            aerogel$dataView = PersistentDataViews.entity(this);
        }
        return aerogel$dataView;
    }

    @Unique
    public VirtualEntity virtual(
        PluginContext plugin, Collection<? extends ServerPlayer> viewers
    ) {
        return Objects.requireNonNull(plugin, "plugin").virtualEntities().show(
            (Entity) (Object) this, Objects.requireNonNull(viewers, "viewers"));
    }

    @Unique
    public VirtualEntity virtual(PluginContext plugin, ServerPlayer viewer) {
        return virtual(plugin, List.of(Objects.requireNonNull(viewer, "viewer")));
    }

    @Override
    public synchronized CompoundTag aerogel$persistentData(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        return aerogel$persistentData.getCompoundOrEmpty(pluginId).copy();
    }

    @Override
    public synchronized void aerogel$editPersistentData(
        String pluginId, Consumer<CompoundTag> editor
    ) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(editor, "editor");
        CompoundTag value = aerogel$persistentData.getCompoundOrEmpty(pluginId).copy();
        editor.accept(value);
        if (value.isEmpty()) aerogel$persistentData.remove(pluginId);
        else aerogel$persistentData.put(pluginId, value);
    }

    @Override
    public synchronized CompoundTag aerogel$allPersistentData() {
        return aerogel$persistentData.copy();
    }

    @Override
    public synchronized void aerogel$restorePersistentData(CompoundTag data) {
        aerogel$persistentData = Objects.requireNonNull(data, "data").copy();
    }

    @Inject(method = "saveWithoutId(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"))
    private void aerogel$savePersistentData(ValueOutput output, CallbackInfo callbackInfo) {
        CompoundTag data = aerogel$allPersistentData();
        if (!data.isEmpty()) output.store("AerogelPersistentData", CompoundTag.CODEC, data);
    }

    @Inject(method = "load(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("TAIL"))
    private void aerogel$loadPersistentData(ValueInput input, CallbackInfo callbackInfo) {
        aerogel$restorePersistentData(
            input.read("AerogelPersistentData", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override
    public byte aerogel$sharedFlags() {
        return ((Entity) (Object) this).getEntityData().get(DATA_SHARED_FLAGS_ID);
    }

    @Override
    public SynchedEntityData.DataValue<Byte> aerogel$sharedFlagsValue(byte flags) {
        return SynchedEntityData.DataValue.create(DATA_SHARED_FLAGS_ID, flags);
    }

    @Unique
    public Collection<Entity> nearbyEntities(double radius) {
        return nearbyEntities(radius, entity -> true);
    }

    @Unique
    public Collection<Entity> nearbyEntities(double radius, Predicate<Entity> filter) {
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) return List.of();
        Collection<Entity> nearby = level.nearbyEntities(
            self.getX(), self.getY(), self.getZ(), radius,
            Objects.requireNonNull(filter, "filter"));
        return nearby.stream().filter(entity -> entity != self).toList();
    }

    @Unique
    public boolean teleport(ServerLevel destination, double x, double y, double z) {
        return teleport(destination, x, y, z,
            ((Entity) (Object) this).getYRot(), ((Entity) (Object) this).getXRot());
    }

    @Unique
    public boolean teleport(
        ServerLevel destination, double x, double y, double z, float yaw, float pitch
    ) {
        Objects.requireNonNull(destination, "destination");
        return ((Entity) (Object) this).teleportTo(
            destination, x, y, z, Set.of(), yaw, pitch, true);
    }

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void aerogel$removed(Entity.RemovalReason reason, CallbackInfo callbackInfo) {
        if (!EventHooks.hasListeners(EntityRemoveEvent.class)) return;
        EventHooks.post(new EntityRemoveEvent((Entity) (Object) this, reason));
    }

    @Inject(method = "markHurt()V", at = @At("RETURN"))
    private void aerogel$hurtStateChanged(CallbackInfo callbackInfo) {
        AerogelRuntime.entityTrackingDirty((Entity) (Object) this);
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$mount(
        Entity vehicle, boolean force, boolean teleport,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (aerogel$mountOverride || !EventHooks.hasListeners(EntityMountEvent.class)) return;
        Entity self = (Entity) (Object) this;
        EntityMountEvent event = new EntityMountEvent(self, vehicle, force);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        } else if (event.vehicle() != vehicle || event.force() != force) {
            aerogel$mountOverride = true;
            try {
                callbackInfo.setReturnValue(self.startRiding(
                    event.vehicle(), event.force(), teleport));
            } finally {
                aerogel$mountOverride = false;
            }
        }
    }

    @Inject(method = "stopRiding()V", at = @At("HEAD"), cancellable = true)
    private void aerogel$dismount(CallbackInfo callbackInfo) {
        if (!EventHooks.hasListeners(EntityDismountEvent.class)) return;
        Entity self = (Entity) (Object) this;
        Entity vehicle = self.getVehicle();
        if (vehicle != null) {
            EntityDismountEvent event = new EntityDismountEvent(self, vehicle);
            EventHooks.post(event);
            if (event.isCancelled()) callbackInfo.cancel();
        }
    }

    @Inject(method = "igniteForTicks(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$combust(int durationTicks, CallbackInfo callbackInfo) {
        if (aerogel$combustOverride || !EventHooks.hasListeners(EntityCombustEvent.class)) return;
        Entity self = (Entity) (Object) this;
        EntityCombustEvent event = new EntityCombustEvent(self, durationTicks);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.durationTicks() != durationTicks) {
            aerogel$combustOverride = true;
            try {
                self.igniteForTicks(event.durationTicks());
            } finally {
                aerogel$combustOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)"
        + "Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void aerogel$teleport(
        TeleportTransition transition, CallbackInfoReturnable<Entity> callbackInfo
    ) {
        if (aerogel$teleportOverride || !EventHooks.hasListeners(EntityTeleportEvent.class)) return;
        Entity self = (Entity) (Object) this;
        EntityTeleportEvent event = new EntityTeleportEvent(self, transition);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(null);
        } else if (event.transition() != transition) {
            aerogel$teleportOverride = true;
            try {
                callbackInfo.setReturnValue(self.teleport(event.transition()));
            } finally {
                aerogel$teleportOverride = false;
            }
        }
    }

    @Inject(method = "setAirSupply(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$airSupply(int airSupply, CallbackInfo callbackInfo) {
        if (aerogel$airOverride || !EventHooks.hasListeners(EntityAirSupplyChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        int previous = self.getAirSupply();
        if (previous == airSupply) return;
        EntityAirSupplyChangeEvent event = new EntityAirSupplyChangeEvent(
            self, previous, airSupply);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.airSupply() != airSupply) {
            aerogel$airOverride = true;
            try {
                self.setAirSupply(event.airSupply());
            } finally {
                aerogel$airOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setTicksFrozen(I)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$freezeTicks(int frozenTicks, CallbackInfo callbackInfo) {
        if (aerogel$freezeOverride || !EventHooks.hasListeners(EntityFreezeTicksChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        int previous = self.getTicksFrozen();
        if (previous == frozenTicks) return;
        EntityFreezeTicksChangeEvent event = new EntityFreezeTicksChangeEvent(
            self, previous, frozenTicks);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.frozenTicks() != frozenTicks) {
            aerogel$freezeOverride = true;
            try {
                self.setTicksFrozen(event.frozenTicks());
            } finally {
                aerogel$freezeOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setPose(Lnet/minecraft/world/entity/Pose;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$pose(Pose pose, CallbackInfo callbackInfo) {
        if (aerogel$poseOverride || !EventHooks.hasListeners(EntityPoseChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        Pose previous = self.getPose();
        if (previous == pose) return;
        EntityPoseChangeEvent event = new EntityPoseChangeEvent(
            self, previous, pose);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.pose() != pose) {
            aerogel$poseOverride = true;
            try {
                self.setPose(event.pose());
            } finally {
                aerogel$poseOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setCustomName(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$customName(Component customName, CallbackInfo callbackInfo) {
        if (aerogel$customNameOverride || !EventHooks.hasListeners(EntityCustomNameChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        Component previous = self.getCustomName();
        if (Objects.equals(previous, customName)) return;
        EntityCustomNameChangeEvent event = new EntityCustomNameChangeEvent(
            self, previous, customName);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (!Objects.equals(event.customName(), customName)) {
            aerogel$customNameOverride = true;
            try {
                self.setCustomName(event.customName());
            } finally {
                aerogel$customNameOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setInvisible(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$visibility(boolean invisible, CallbackInfo callbackInfo) {
        if (aerogel$visibilityOverride || !EventHooks.hasListeners(EntityVisibilityChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        boolean previous = self.isInvisible();
        if (previous == invisible) return;
        EntityVisibilityChangeEvent event = new EntityVisibilityChangeEvent(
            self, previous, invisible);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.invisible() != invisible) {
            aerogel$visibilityOverride = true;
            try {
                self.setInvisible(event.invisible());
            } finally {
                aerogel$visibilityOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setNoGravity(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$gravity(boolean noGravity, CallbackInfo callbackInfo) {
        if (aerogel$gravityOverride || !EventHooks.hasListeners(EntityGravityChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        boolean previous = self.isNoGravity();
        if (previous == noGravity) return;
        EntityGravityChangeEvent event = new EntityGravityChangeEvent(
            self, previous, noGravity);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.noGravity() != noGravity) {
            aerogel$gravityOverride = true;
            try {
                self.setNoGravity(event.noGravity());
            } finally {
                aerogel$gravityOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setSilent(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$silent(boolean silent, CallbackInfo callbackInfo) {
        if (aerogel$silentOverride || !EventHooks.hasListeners(EntitySilentChangeEvent.class)) return;
        Entity self = (Entity) (Object) this;
        boolean previous = self.isSilent();
        if (previous == silent) return;
        EntitySilentChangeEvent event = new EntitySilentChangeEvent(
            self, previous, silent);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.silent() != silent) {
            aerogel$silentOverride = true;
            try {
                self.setSilent(event.silent());
            } finally {
                aerogel$silentOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setShiftKeyDown(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$sneaking(boolean sneaking, CallbackInfo callbackInfo) {
        if (aerogel$playerStateOverride
            || !EventHooks.hasListeners(PlayerSneakChangeEvent.class)
            || !((Object) this instanceof ServerPlayer player)) return;
        boolean previous = player.isShiftKeyDown();
        if (previous == sneaking) return;
        PlayerSneakChangeEvent event = new PlayerSneakChangeEvent(
            player, previous, sneaking);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.sneaking() != sneaking) {
            aerogel$playerStateOverride = true;
            try {
                player.setShiftKeyDown(event.sneaking());
            } finally {
                aerogel$playerStateOverride = false;
            }
            callbackInfo.cancel();
        }
    }

    @Inject(method = "setSwimming(Z)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$swimming(boolean swimming, CallbackInfo callbackInfo) {
        if (aerogel$playerStateOverride
            || !EventHooks.hasListeners(PlayerSwimChangeEvent.class)
            || !((Object) this instanceof ServerPlayer player)) return;
        boolean previous = player.isSwimming();
        if (previous == swimming) return;
        PlayerSwimChangeEvent event = new PlayerSwimChangeEvent(
            player, previous, swimming);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (event.swimming() != swimming) {
            aerogel$playerStateOverride = true;
            try {
                player.setSwimming(event.swimming());
            } finally {
                aerogel$playerStateOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
