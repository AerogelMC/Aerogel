package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.entity.EntitySpawnEvent;
import dev.aerogel.api.event.entity.ProjectileLaunchEvent;
import dev.aerogel.api.event.world.ChunkLoadEvent;
import dev.aerogel.api.event.world.ChunkPreUnloadEvent;
import dev.aerogel.api.event.world.ChunkUnloadEvent;
import dev.aerogel.api.event.world.ExplosionEvent;
import dev.aerogel.api.event.world.RainChangeEvent;
import dev.aerogel.api.event.world.ThunderChangeEvent;
import dev.aerogel.api.event.world.WorldLoadEvent;
import dev.aerogel.api.event.world.WorldUnloadEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel")
abstract class ServerLevelMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$worldLoaded(CallbackInfo callbackInfo) {
        EventHooks.post(new WorldLoadEvent(EventHooks.cast(this)));
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void aerogel$worldUnloaded(CallbackInfo callbackInfo) {
        EventHooks.post(new WorldUnloadEvent(EventHooks.cast(this)));
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
        if (EventHooks.isInstance(entity, "net.minecraft.world.entity.projectile.Projectile")) {
            ProjectileLaunchEvent projectileEvent = new ProjectileLaunchEvent(
                EventHooks.cast(this), EventHooks.cast(entity));
            EventHooks.post(projectileEvent);
            if (projectileEvent.isCancelled()) {
                callbackInfo.setReturnValue(false);
                return;
            }
        }
        EntitySpawnEvent event = new EntitySpawnEvent(
            EventHooks.cast(this), EventHooks.cast(entity));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "startTickingChunk(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("RETURN"))
    private void aerogel$chunkLoaded(@Coerce Object chunk, CallbackInfo callbackInfo) {
        EventHooks.post(new ChunkLoadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD"))
    private void aerogel$chunkUnload(@Coerce Object chunk, CallbackInfo callbackInfo) {
        EventHooks.post(new ChunkPreUnloadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("RETURN"))
    private void aerogel$chunkUnloaded(@Coerce Object chunk, CallbackInfo callbackInfo) {
        EventHooks.post(new ChunkUnloadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
    }

    @Redirect(
        method = "advanceWeatherCycle()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/WeatherData;setRaining(Z)V")
    )
    private void aerogel$rainChange(@Coerce Object weather, boolean raining) {
        boolean previous = (Boolean) EventHooks.call(weather, "isRaining");
        if (previous == raining) {
            EventHooks.call(weather, "setRaining", raining);
            return;
        }
        RainChangeEvent event = new RainChangeEvent(EventHooks.cast(this), raining);
        EventHooks.post(event);
        if (!event.isCancelled()) EventHooks.call(weather, "setRaining", raining);
    }

    @Redirect(
        method = "advanceWeatherCycle()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/WeatherData;setThundering(Z)V")
    )
    private void aerogel$thunderChange(@Coerce Object weather, boolean thundering) {
        boolean previous = (Boolean) EventHooks.call(weather, "isThundering");
        if (previous == thundering) {
            EventHooks.call(weather, "setThundering", thundering);
            return;
        }
        ThunderChangeEvent event = new ThunderChangeEvent(EventHooks.cast(this), thundering);
        EventHooks.post(event);
        if (!event.isCancelled()) EventHooks.call(weather, "setThundering", thundering);
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
        ExplosionEvent event = new ExplosionEvent(
            EventHooks.cast(this), EventHooks.cast(source), x, y, z, radius);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
