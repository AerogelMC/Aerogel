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
import dev.aerogel.loader.internal.DeathDropCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.server.level.ServerLevel")
abstract class ServerLevelMixin {
    @Unique private boolean aerogel$explosionOverride;

    @Unique
    public String identifier() {
        return String.valueOf(EventHooks.call(EventHooks.call(this, "dimension"), "identifier"));
    }

    @Unique
    public Collection<Entity> entities() {
        Iterable<?> entities = (Iterable<?>) EventHooks.call(this, "getAllEntities");
        List<Entity> result = new ArrayList<>();
        for (Object entity : entities) result.add(EventHooks.cast(entity));
        return List.copyOf(result);
    }

    @Unique
    public Optional<Entity> findEntity(UUID uniqueId) {
        return Optional.ofNullable(EventHooks.cast(EventHooks.call(this, "getEntityInAnyDimension",
            Objects.requireNonNull(uniqueId, "uniqueId"))));
    }

    @Unique
    public Optional<Entity> findEntity(int entityId) {
        return Optional.ofNullable(EventHooks.cast(EventHooks.call(this, "getEntity", entityId)));
    }

    @Unique
    public Collection<Entity> nearbyEntities(double x, double y, double z, double radius) {
        return nearbyEntities(x, y, z, radius, entity -> true);
    }

    @Unique
    public Collection<Entity> nearbyEntities(
        double centerX, double centerY, double centerZ, double radius, Predicate<Entity> filter
    ) {
        Objects.requireNonNull(filter, "filter");
        if (!Double.isFinite(radius) || radius < 0.0D) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        double maximumDistance = radius * radius;
        List<Entity> result = new ArrayList<>();
        for (Entity entity : entities()) {
            double x = entity.getX() - centerX;
            double y = entity.getY() - centerY;
            double z = entity.getZ() - centerZ;
            if (x * x + y * y + z * z <= maximumDistance && filter.test(entity)) result.add(entity);
        }
        return List.copyOf(result);
    }

    @Unique
    private void aerogel$weather(int value, int durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must not be negative");
        boolean raining = value > 0;
        boolean thundering = value > 1;
        Object weather = EventHooks.call(this, "getWeatherData");
        EventHooks.call(weather, "setClearWeatherTime", value == 0 ? durationTicks : 0);
        EventHooks.call(weather, "setRainTime", raining ? durationTicks : 0);
        EventHooks.call(weather, "setThunderTime", thundering ? durationTicks : 0);
        EventHooks.call(weather, "setRaining", raining);
        EventHooks.call(weather, "setThundering", thundering);
    }

    @Unique
    public void clearWeather(int durationTicks) {
        aerogel$weather(0, durationTicks);
    }

    @Unique
    public void rain(int durationTicks) {
        aerogel$weather(1, durationTicks);
    }

    @Unique
    public void thunder(int durationTicks) {
        aerogel$weather(2, durationTicks);
    }

    @Unique
    public BlockState block(int x, int y, int z) {
        return EventHooks.cast(EventHooks.call(this, "getBlockState", EventHooks.construct(this,
            "net.minecraft.core.BlockPos", x, y, z)));
    }

    @Unique
    public boolean block(int x, int y, int z, BlockState state, int flags) {
        return (boolean) EventHooks.call(this, "setBlock", EventHooks.construct(this,
            "net.minecraft.core.BlockPos", x, y, z), Objects.requireNonNull(state, "state"), flags);
    }

    @Unique
    public boolean spawn(Entity entity) {
        return (boolean) EventHooks.call(this, "addFreshEntity", Objects.requireNonNull(entity, "entity"));
    }

    @Unique
    public boolean teleport(ServerPlayer player, double x, double y, double z) {
        Objects.requireNonNull(player, "player");
        return teleport(player, x, y, z,
            ((Number) EventHooks.call(player, "getYRot")).floatValue(),
            ((Number) EventHooks.call(player, "getXRot")).floatValue());
    }

    @Unique
    public boolean teleport(
        ServerPlayer player, double x, double y, double z, float yaw, float pitch
    ) {
        Objects.requireNonNull(player, "player");
        return (boolean) EventHooks.call(player, "teleportTo", this,
            x, y, z, Set.of(), yaw, pitch, true);
    }

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
        if (DeathDropCapture.capture(EventHooks.cast(entity))) {
            callbackInfo.setReturnValue(true);
            return;
        }
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
        if (aerogel$explosionOverride) return;
        ExplosionEvent event = new ExplosionEvent(
            EventHooks.cast(this), EventHooks.cast(source), x, y, z, radius, fire);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        } else if (Double.compare(event.x(), x) != 0
            || Double.compare(event.y(), y) != 0
            || Double.compare(event.z(), z) != 0
            || Float.compare(event.radius(), radius) != 0
            || event.fire() != fire) {
            aerogel$explosionOverride = true;
            try {
                EventHooks.call(this, "explode", source, damageSource, calculator,
                    event.x(), event.y(), event.z(), event.radius(), event.fire(), interaction,
                    smallParticle, largeParticle, particles, sound);
            } finally {
                aerogel$explosionOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
