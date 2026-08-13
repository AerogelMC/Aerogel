package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntitySpawnEvent;
import dev.aerogel.api.event.world.ExplosionEvent;
import dev.aerogel.api.event.world.WorldLoadEvent;
import dev.aerogel.api.event.world.WorldUnloadEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel")
abstract class ServerLevelMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$worldLoaded(CallbackInfo callbackInfo) {
        EventHooks.post(new WorldLoadEvent(this));
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void aerogel$worldUnloaded(CallbackInfo callbackInfo) {
        EventHooks.post(new WorldUnloadEvent(this));
    }

    @Inject(
        method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$beforeEntitySpawn(
        @Coerce Object entity,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EntitySpawnEvent event = new EntitySpawnEvent(this, entity);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void aerogel$explode(
        @Coerce Object source,
        @Coerce Object damageSource,
        @Coerce Object calculator,
        double x, double y, double z, float radius, boolean fire,
        @Coerce Object interaction,
        @Coerce Object smallParticle,
        @Coerce Object largeParticle,
        @Coerce Object particles,
        @Coerce Object sound,
        CallbackInfo callbackInfo
    ) {
        ExplosionEvent event = new ExplosionEvent(this, source, x, y, z, radius);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
