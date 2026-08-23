package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.syncher.SynchedEntityData")
abstract class SynchedEntityDataMixin {
    @Shadow @Final private SyncedDataHolder entity;
    @Shadow private boolean isDirty;

    @Inject(
        method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
        at = @At("RETURN")
    )
    private <T> void aerogel$dirtyStateChanged(
        EntityDataAccessor<T> accessor,
        T value,
        boolean force,
        CallbackInfo callbackInfo
    ) {
        if (isDirty && entity instanceof Entity tracked) {
            AerogelRuntime.entityTrackingDirty(tracked);
        }
    }
}
