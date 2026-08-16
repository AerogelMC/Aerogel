package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.block.BlockStateChangeEvent;
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
import dev.aerogel.loader.event.BlockChangeContext;
import dev.aerogel.loader.internal.DeathDropCapture;
import dev.aerogel.api.persistence.PersistentDataView;
import dev.aerogel.api.blockbatch.BlockBatch;
import dev.aerogel.loader.internal.PersistentDataViews;
import dev.aerogel.loader.api.DirectBlockBatchService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.WeatherData;

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
    public PersistentDataView data() {
        return PersistentDataViews.world((ServerLevel) (Object) this);
    }

    @Unique
    public PersistentDataView data(BlockPos position) {
        return PersistentDataViews.block(
            (ServerLevel) (Object) this, Objects.requireNonNull(position, "position"));
    }

    @Unique
    public BlockBatch batch() {
        return DirectBlockBatchService.direct((ServerLevel) (Object) this);
    }

    @Redirect(
        method = "tickBlock(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/"
            + "BlockState;tick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V")
    )
    private void aerogel$scheduledBlockTick(
        BlockState state, ServerLevel level, BlockPos position, RandomSource random
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            state.tick(level, position, random);
            return;
        }
        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.SCHEDULED_TICK, null, position, null,
            () -> state.tick(level, position, random));
    }

    @Redirect(
        method = "tickFluid(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/material/Fluid;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/"
            + "FluidState;tick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)V")
    )
    private void aerogel$scheduledFluidTick(
        FluidState fluidState, ServerLevel level, BlockPos position, BlockState blockState
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            fluidState.tick(level, position, blockState);
            return;
        }
        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.FLUID, null, position, null,
            () -> fluidState.tick(level, position, blockState));
    }

    @Redirect(
        method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/"
            + "BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V")
    )
    private void aerogel$randomBlockTick(
        BlockState state, ServerLevel level, BlockPos position, RandomSource random
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            state.randomTick(level, position, random);
            return;
        }
        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.RANDOM_TICK, null, position, null,
            () -> state.randomTick(level, position, random));
    }

    @Redirect(
        method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/"
            + "FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V")
    )
    private void aerogel$randomFluidTick(
        FluidState state, ServerLevel level, BlockPos position, RandomSource random
    ) {
        if (!EventHooks.hasListeners(BlockStateChangeEvent.class)) {
            state.randomTick(level, position, random);
            return;
        }
        BlockChangeContext.run(
            BlockStateChangeEvent.Reason.RANDOM_TICK, null, position, null,
            () -> state.randomTick(level, position, random));
    }

    @Unique
    public String identifier() {
        return String.valueOf(((ServerLevel) (Object) this).dimension().identifier());
    }

    @Unique
    public Collection<Entity> entities() {
        Iterable<Entity> entities = ((ServerLevel) (Object) this).getAllEntities();
        List<Entity> result = new ArrayList<>();
        for (Entity entity : entities) result.add(entity);
        return List.copyOf(result);
    }

    @Unique
    public Optional<Entity> findEntity(UUID uniqueId) {
        return Optional.ofNullable(((ServerLevel) (Object) this).getEntityInAnyDimension(
            Objects.requireNonNull(uniqueId, "uniqueId")));
    }

    @Unique
    public Optional<Entity> findEntity(int entityId) {
        return Optional.ofNullable(((ServerLevel) (Object) this).getEntity(entityId));
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
        WeatherData weather = ((ServerLevel) (Object) this).getWeatherData();
        weather.setClearWeatherTime(value == 0 ? durationTicks : 0);
        weather.setRainTime(raining ? durationTicks : 0);
        weather.setThunderTime(thundering ? durationTicks : 0);
        weather.setRaining(raining);
        weather.setThundering(thundering);
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
        return ((ServerLevel) (Object) this).getBlockState(new BlockPos(x, y, z));
    }

    @Unique
    public boolean block(int x, int y, int z, BlockState state, int flags) {
        return ((ServerLevel) (Object) this).setBlock(
            new BlockPos(x, y, z), Objects.requireNonNull(state, "state"), flags);
    }

    @Unique
    public boolean spawn(Entity entity) {
        return ((ServerLevel) (Object) this).addFreshEntity(
            Objects.requireNonNull(entity, "entity"));
    }

    @Unique
    public boolean teleport(ServerPlayer player, double x, double y, double z) {
        Objects.requireNonNull(player, "player");
        return teleport(player, x, y, z,
            player.getYRot(), player.getXRot());
    }

    @Unique
    public boolean teleport(
        ServerPlayer player, double x, double y, double z, float yaw, float pitch
    ) {
        Objects.requireNonNull(player, "player");
        return player.teleportTo((ServerLevel) (Object) this,
            x, y, z, Set.of(), yaw, pitch, true);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$worldLoaded(CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(WorldLoadEvent.class)) {
            EventHooks.post(new WorldLoadEvent(EventHooks.cast(this)));
        }
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void aerogel$worldUnloaded(CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(WorldUnloadEvent.class)) {
            EventHooks.post(new WorldUnloadEvent(EventHooks.cast(this)));
        }
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
        if (EventHooks.hasListeners(ProjectileLaunchEvent.class)
            && entity instanceof Projectile) {
            ProjectileLaunchEvent projectileEvent = new ProjectileLaunchEvent(
                EventHooks.cast(this), EventHooks.cast(entity));
            EventHooks.post(projectileEvent);
            if (projectileEvent.isCancelled()) {
                callbackInfo.setReturnValue(false);
                return;
            }
        }
        if (EventHooks.hasListeners(EntitySpawnEvent.class)) {
            EntitySpawnEvent event = new EntitySpawnEvent(
                EventHooks.cast(this), EventHooks.cast(entity));
            EventHooks.post(event);
            if (event.isCancelled()) {
                callbackInfo.setReturnValue(false);
            }
        }
    }

    @Inject(method = "startTickingChunk(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("RETURN"))
    private void aerogel$chunkLoaded(@Coerce Object chunk, CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(ChunkLoadEvent.class)) {
            EventHooks.post(new ChunkLoadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
        }
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD"))
    private void aerogel$chunkUnload(@Coerce Object chunk, CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(ChunkPreUnloadEvent.class)) {
            EventHooks.post(new ChunkPreUnloadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
        }
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("RETURN"))
    private void aerogel$chunkUnloaded(@Coerce Object chunk, CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(ChunkUnloadEvent.class)) {
            EventHooks.post(new ChunkUnloadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
        }
    }

    @Redirect(
        method = "advanceWeatherCycle()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/WeatherData;setRaining(Z)V")
    )
    private void aerogel$rainChange(WeatherData weather, boolean raining) {
        if (!EventHooks.hasListeners(RainChangeEvent.class)) {
            weather.setRaining(raining);
            return;
        }
        boolean previous = weather.isRaining();
        if (previous == raining) {
            weather.setRaining(raining);
            return;
        }
        RainChangeEvent event = new RainChangeEvent(EventHooks.cast(this), raining);
        EventHooks.post(event);
        if (!event.isCancelled()) weather.setRaining(raining);
    }

    @Redirect(
        method = "advanceWeatherCycle()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/saveddata/WeatherData;setThundering(Z)V")
    )
    private void aerogel$thunderChange(WeatherData weather, boolean thundering) {
        if (!EventHooks.hasListeners(ThunderChangeEvent.class)) {
            weather.setThundering(thundering);
            return;
        }
        boolean previous = weather.isThundering();
        if (previous == thundering) {
            weather.setThundering(thundering);
            return;
        }
        ThunderChangeEvent event = new ThunderChangeEvent(EventHooks.cast(this), thundering);
        EventHooks.post(event);
        if (!event.isCancelled()) weather.setThundering(thundering);
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void aerogel$explode(
        Entity source,
        DamageSource damageSource,
        ExplosionDamageCalculator calculator,
        double x, double y, double z, float radius, boolean fire,
        Level.ExplosionInteraction interaction,
        ParticleOptions smallParticle,
        ParticleOptions largeParticle,
        WeightedList<ExplosionParticleInfo> particles,
        Holder<SoundEvent> sound,
        CallbackInfo callbackInfo
    ) {
        if (aerogel$explosionOverride || !EventHooks.hasListeners(ExplosionEvent.class)) return;
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
                ((ServerLevel) (Object) this).explode(source, damageSource, calculator,
                    event.x(), event.y(), event.z(), event.radius(), event.fire(), interaction,
                    smallParticle, largeParticle, particles, sound);
            } finally {
                aerogel$explosionOverride = false;
            }
            callbackInfo.cancel();
        }
    }
}
