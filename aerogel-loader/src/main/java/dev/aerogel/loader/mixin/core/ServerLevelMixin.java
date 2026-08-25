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
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.context.ContextRandomRouting;
import dev.aerogel.loader.context.ContextNeighborRouting;
import dev.aerogel.loader.context.LevelNeighborUpdaterBridge;
import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.ConcurrentNavigationSet;
import dev.aerogel.loader.internal.NavigationIndexBridge;
import dev.aerogel.loader.internal.EntityLoadStatusBridge;
import dev.aerogel.loader.internal.LevelTicksBridge;
import dev.aerogel.loader.internal.DistanceManagerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
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
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.Vec3;

@Mixin(targets = "net.minecraft.server.level.ServerLevel")
abstract class ServerLevelMixin implements NavigationIndexBridge {
    @Shadow @Final @Mutable private List<ServerPlayer> players;
    @Shadow @Final @Mutable private Set<Mob> navigatingMobs;
    @Shadow @Final private LevelTicks<Block> blockTicks;
    @Shadow @Final private LevelTicks<Fluid> fluidTicks;
    @Shadow @Final private PersistentEntitySectionManager<?> entityManager;
    @Shadow public abstract net.minecraft.server.level.ServerChunkCache getChunkSource();
    @Unique private boolean aerogel$explosionOverride;
    @Unique private static final ThreadLocal<AerogelNavigationInvalidations>
        AEROGEL$NAVIGATION_INVALIDATIONS = new ThreadLocal<>();

    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;"
        + "Lnet/minecraft/world/phys/Vec3;"
        + "Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$routeGameEvent(
        Holder<GameEvent> event, Vec3 position, GameEvent.Context context,
        CallbackInfo callback
    ) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (AerogelRuntime.routeGameEvent(level, event, position, context)) {
            callback.cancel();
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$publishPlayerIndexConcurrently(CallbackInfo callback) {
        players = new CopyOnWriteArrayList<>(players);
        Set<Mob> concurrentNavigations = new ConcurrentNavigationSet();
        concurrentNavigations.addAll(navigatingMobs);
        navigatingMobs = concurrentNavigations;
        ((EntityLoadStatusBridge) (Object) entityManager)
            .aerogel$loadStatusListener(this::aerogel$scheduledTickEligibilityChanged);
        ((EntityLoadStatusBridge) (Object) entityManager)
            .aerogel$level((ServerLevel) (Object) this);
        ((DistanceManagerBridge) getChunkSource().chunkMap.getDistanceManager())
            .aerogel$blockTickingListener(this::aerogel$scheduledTickEligibilityChanged);
    }

    @Unique
    private void aerogel$scheduledTickEligibilityChanged(long chunkKey) {
        ((LevelTicksBridge) (Object) blockTicks).aerogel$eligibilityChanged(chunkKey);
        ((LevelTicksBridge) (Object) fluidTicks).aerogel$eligibilityChanged(chunkKey);
    }

    @Inject(method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/world/level/block/state/BlockState;I)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitBlockUpdateSideEffects(
        BlockPos position, BlockState previousState, BlockState state, int flags,
        CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        AerogelBlockUpdateBatch batch = NativeTickCoordinator.nativeAttachment(
            this, () -> {
                AerogelBlockUpdateBatch created = new AerogelBlockUpdateBatch(
                    (ServerLevel) (Object) this, navigatingMobs);
                NativeTickCoordinator.deferGlobalCommit(created::commit);
                NativeTickCoordinator.deferNativeCompletion(created::dispatchNavigations);
                return created;
            });
        if (batch != null) {
            batch.add(position.immutable(), previousState, state, flags);
            callback.cancel();
        }
    }

    @Redirect(
        method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/block/state/BlockState;I)V",
        at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;")
    )
    private Iterator<Mob> aerogel$routeNavigationInvalidation(
        Set<Mob> mobs, BlockPos position, BlockState previousState,
        BlockState state, int flags
    ) {
        AerogelNavigationInvalidations invalidations =
            AEROGEL$NAVIGATION_INVALIDATIONS.get();
        if (invalidations != null) return Collections.emptyIterator();
        if (mobs instanceof ConcurrentNavigationSet indexed) {
            return indexed.candidates(position);
        }
        return mobs.iterator();
    }

    @Override
    public void aerogel$beginNavigationUpdate(Mob mob) {
        if (navigatingMobs instanceof ConcurrentNavigationSet indexed) {
            indexed.beginUpdate(mob);
        }
    }

    @Override
    public void aerogel$finishNavigationUpdate(Mob mob) {
        if (navigatingMobs instanceof ConcurrentNavigationSet indexed) {
            indexed.finishUpdate(mob);
        }
    }

    @Unique
    private record AerogelBlockUpdate(
        BlockPos position, BlockState previousState, BlockState state, int flags
    ) { }

    @Unique
    private static final class AerogelBlockUpdateBatch {
        private final ServerLevel level;
        private final Set<Mob> navigatingMobs;
        private final List<AerogelBlockUpdate> updates = new ArrayList<>();
        private final List<BlockPos> collisionChanges = new ArrayList<>();

        private AerogelBlockUpdateBatch(ServerLevel level, Set<Mob> navigatingMobs) {
            this.level = level;
            this.navigatingMobs = navigatingMobs;
        }

        private void add(
            BlockPos position, BlockState previousState, BlockState state, int flags
        ) {
            updates.add(new AerogelBlockUpdate(position, previousState, state, flags));
            if (Shapes.joinIsNotEmpty(
                previousState.getCollisionShape(level, position),
                state.getCollisionShape(level, position), BooleanOp.NOT_SAME)) {
                collisionChanges.add(position);
            }
        }

        private void commit() {
            AerogelNavigationInvalidations invalidations =
                new AerogelNavigationInvalidations();
            AEROGEL$NAVIGATION_INVALIDATIONS.set(invalidations);
            try {
                for (AerogelBlockUpdate update : updates) {
                    level.sendBlockUpdated(update.position(), update.previousState(),
                        update.state(), update.flags());
                }
            } finally {
                AEROGEL$NAVIGATION_INVALIDATIONS.remove();
            }
        }

        private void dispatchNavigations() {
            if (navigatingMobs.isEmpty() || collisionChanges.isEmpty()) return;
            List<BlockPos> changedPositions = List.copyOf(collisionChanges);
            Collection<? extends Mob> candidates =
                navigatingMobs instanceof ConcurrentNavigationSet indexed
                    ? indexed.candidates(changedPositions)
                    : new ArrayList<>(navigatingMobs);
            List<Entity> affected = new ArrayList<>(candidates);
            AerogelRuntime.routeOwnedEntityBatch(level, affected, entity -> {
                PathNavigation navigation = ((Mob) entity).getNavigation();
                for (BlockPos position : changedPositions) {
                    if (navigation.shouldRecomputePath(position)) {
                        navigation.recomputePath();
                        break;
                    }
                }
            });
        }
    }

    @Unique
    private static final class AerogelNavigationInvalidations {
    }

    @Inject(method = "blockEvent(Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/Block;II)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitBlockEvent(
        BlockPos position, Block block, int type, int data, CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        BlockPos immutablePosition = position.immutable();
        if (NativeTickCoordinator.deferGlobalCommit(() ->
            ((ServerLevel) (Object) this).blockEvent(
                immutablePosition, block, type, data))) callback.cancel();
    }

    @Redirect(
        method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/world/level/redstone/Orientation;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;"
            + "neighborUpdater:Lnet/minecraft/world/level/redstone/CollectingNeighborUpdater;")
    )
    private CollectingNeighborUpdater aerogel$contextNeighborUpdaterAt(
        ServerLevel level, BlockPos position, Block block, Orientation orientation
    ) {
        CollectingNeighborUpdater fallback =
            ((LevelNeighborUpdaterBridge) (Object) level).aerogel$neighborUpdater();
        return ContextNeighborRouting.current(level, fallback, position);
    }

    @Redirect(
        method = "updateNeighborsAtExceptFromFacing(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/Direction;"
            + "Lnet/minecraft/world/level/redstone/Orientation;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;"
            + "neighborUpdater:Lnet/minecraft/world/level/redstone/CollectingNeighborUpdater;")
    )
    private CollectingNeighborUpdater aerogel$contextNeighborUpdaterExcept(
        ServerLevel level, BlockPos position, Block block,
        Direction direction, Orientation orientation
    ) {
        CollectingNeighborUpdater fallback =
            ((LevelNeighborUpdaterBridge) (Object) level).aerogel$neighborUpdater();
        return ContextNeighborRouting.current(level, fallback, position);
    }

    @Redirect(
        method = "neighborChanged(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/world/level/redstone/Orientation;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;"
            + "neighborUpdater:Lnet/minecraft/world/level/redstone/CollectingNeighborUpdater;")
    )
    private CollectingNeighborUpdater aerogel$contextNeighborUpdaterChanged(
        ServerLevel level, BlockPos position, Block block, Orientation orientation
    ) {
        CollectingNeighborUpdater fallback =
            ((LevelNeighborUpdaterBridge) (Object) level).aerogel$neighborUpdater();
        return ContextNeighborRouting.current(level, fallback, position);
    }

    @Redirect(
        method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/world/level/redstone/Orientation;Z)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;"
            + "neighborUpdater:Lnet/minecraft/world/level/redstone/CollectingNeighborUpdater;")
    )
    private CollectingNeighborUpdater aerogel$contextNeighborUpdaterChangedState(
        ServerLevel level, BlockState state, BlockPos position, Block block,
        Orientation orientation, boolean moved
    ) {
        CollectingNeighborUpdater fallback =
            ((LevelNeighborUpdaterBridge) (Object) level).aerogel$neighborUpdater();
        return ContextNeighborRouting.current(level, fallback, position);
    }

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

    @Inject(method = "tickBlock(Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/Block;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$routeScheduledBlockTick(
        BlockPos position, Block block, CallbackInfo callback
    ) {
        if (NativeTickCoordinator.isNativeWorker()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (AerogelRuntime.routeBlockTask(level, position,
            () -> level.tickBlock(position, block))) callback.cancel();
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

    @Inject(method = "tickFluid(Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/material/Fluid;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$routeScheduledFluidTick(
        BlockPos position, Fluid fluid, CallbackInfo callback
    ) {
        if (NativeTickCoordinator.isNativeWorker()) return;
        ServerLevel level = (ServerLevel) (Object) this;
        if (AerogelRuntime.routeBlockTask(level, position,
            () -> level.tickFluid(position, fluid))) callback.cancel();
    }

    @Redirect(
        method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/level/ServerLevel;"
                + "random:Lnet/minecraft/util/RandomSource;")
    )
    private RandomSource aerogel$chunkRandom(ServerLevel level) {
        RandomSource owned = ContextRandomRouting.current(level);
        return owned != null ? owned : level.getRandom();
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
        Runnable tick = !EventHooks.hasListeners(BlockStateChangeEvent.class)
            ? () -> state.randomTick(level, position, random)
            : () -> BlockChangeContext.run(
                BlockStateChangeEvent.Reason.RANDOM_TICK, null, position, null,
                () -> state.randomTick(level, position, random));
        if (!NativeTickCoordinator.isNativeWorker()
            || !AerogelRuntime.routeBlockTask(level, position, tick)) tick.run();
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
        Runnable tick = !EventHooks.hasListeners(BlockStateChangeEvent.class)
            ? () -> state.randomTick(level, position, random)
            : () -> BlockChangeContext.run(
                BlockStateChangeEvent.Reason.RANDOM_TICK, null, position, null,
                () -> state.randomTick(level, position, random));
        if (!NativeTickCoordinator.isNativeWorker()
            || !AerogelRuntime.routeBlockTask(level, position, tick)) tick.run();
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
        AerogelRuntime.worldLoaded((ServerLevel) (Object) this);
        if (EventHooks.hasListeners(WorldLoadEvent.class)) {
            EventHooks.post(new WorldLoadEvent(EventHooks.cast(this)));
        }
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void aerogel$worldUnloaded(CallbackInfo callbackInfo) {
        AerogelRuntime.worldUnloaded((ServerLevel) (Object) this);
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
        AerogelRuntime.chunkLoaded(
            (ServerLevel) (Object) this,
            (net.minecraft.world.level.chunk.LevelChunk) chunk);
        if (EventHooks.hasListeners(ChunkLoadEvent.class)) {
            EventHooks.post(new ChunkLoadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
        }
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$chunkUnload(@Coerce Object chunk, CallbackInfo callbackInfo) {
        ServerLevel level = (ServerLevel) (Object) this;
        net.minecraft.world.level.chunk.LevelChunk levelChunk =
            (net.minecraft.world.level.chunk.LevelChunk) chunk;
        if (AerogelRuntime.drainBeforeChunkUnload(
            level, levelChunk, () -> level.unload(levelChunk))) {
            callbackInfo.cancel();
            return;
        }
        if (EventHooks.hasListeners(ChunkPreUnloadEvent.class)) {
            EventHooks.post(new ChunkPreUnloadEvent(EventHooks.cast(this), EventHooks.cast(chunk)));
        }
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("RETURN"))
    private void aerogel$chunkUnloaded(@Coerce Object chunk, CallbackInfo callbackInfo) {
        AerogelRuntime.chunkUnloaded(
            (ServerLevel) (Object) this,
            (net.minecraft.world.level.chunk.LevelChunk) chunk);
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
    @Redirect(
        method = "tick(Ljava/util/function/BooleanSupplier;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/EntityTickList;"
                + "forEach(Ljava/util/function/Consumer;)V")
    )
    private void aerogel$parallelEntityTick(
        net.minecraft.world.level.entity.EntityTickList list,
        java.util.function.Consumer<Entity> action
    ) {
        AerogelRuntime.tickRegisteredEntities((ServerLevel) (Object) this, action);
    }
}
