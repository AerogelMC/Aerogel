package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import java.util.List;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.entity.Entity;

@Mixin(targets = "net.minecraft.server.level.ServerChunkCache")
abstract class ServerChunkCacheMixin {
    @Shadow @Final private Thread mainThread;
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private ChunkMap chunkMap;
    @Unique private NaturalSpawner.SpawnState aerogel$currentSpawnState;
    @Shadow public abstract ChunkAccess getChunk(
        int chunkX, int chunkZ, ChunkStatus targetStatus, boolean create);

    @Invoker("getVisibleChunkIfPresent")
    protected abstract ChunkHolder aerogel$getVisibleChunk(long packedPosition);

    @Invoker("tickSpawningChunk")
    protected abstract void aerogel$invokeTickSpawningChunk(
        LevelChunk chunk, long inhabitedTimeDelta, List<MobCategory> categories,
        NaturalSpawner.SpawnState spawnState);

    @Inject(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$getVisibleChunkNow(
        int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> callback
    ) {
        if (Thread.currentThread() == mainThread || !NativeTickCoordinator.isNativeWorker()) return;
        ChunkHolder holder = aerogel$getVisibleChunk(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess present = holder == null
            ? null : holder.getChunkIfPresent(ChunkStatus.FULL);
        callback.setReturnValue(present instanceof LevelChunk chunk ? chunk : null);
    }

    @Inject(
        method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$routeNativeChunkAccess(
        int chunkX, int chunkZ, ChunkStatus targetStatus, boolean create,
        CallbackInfoReturnable<ChunkAccess> callback
    ) {
        if (Thread.currentThread() == mainThread || !NativeTickCoordinator.isNativeWorker()) return;
        ChunkHolder holder = aerogel$getVisibleChunk(ChunkPos.pack(chunkX, chunkZ));
        ChunkAccess present = holder == null ? null : holder.getChunkIfPresent(targetStatus);
        if (present != null || !create) {
            callback.setReturnValue(present);
            return;
        }

        ChunkResult<ChunkAccess> result = ((ServerChunkCache) (Object) this)
            .getChunkFuture(chunkX, chunkZ, targetStatus, true).join();
        ChunkAccess loaded = result.orElse(null);
        if (loaded == null) {
            throw new IllegalStateException(
                "Chunk generation did not produce " + chunkX + "," + chunkZ
                    + " at status " + targetStatus + ": " + result.getError());
        }
        callback.setReturnValue(loaded);
    }

    @Inject(method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitPlayerDistanceIndex(
        net.minecraft.server.level.ServerPlayer player, CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        SectionPos section = SectionPos.of(player);
        ChunkPos chunk = player.chunkPosition();
        if (NativeTickCoordinator.deferGlobalCommit(
            () -> {
                if (!player.isRemoved()) {
                    ((ChunkMapTrackingBridge) (Object) chunkMap)
                        .aerogel$moveSnapshot(player, section, chunk);
                }
            })) callback.cancel();
    }

    @Inject(method = "blockChanged(Lnet/minecraft/core/BlockPos;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitChangedBlock(BlockPos position, CallbackInfo callback) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        BlockPos immutablePosition = position.immutable();
        if (NativeTickCoordinator.deferGlobalCommit(
            () -> ((ServerChunkCache) (Object) this).blockChanged(immutablePosition))) {
            callback.cancel();
        }
    }

    @Redirect(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;"
            + "createState(ILjava/lang/Iterable;"
            + "Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;"
            + "Lnet/minecraft/world/level/LocalMobCapCalculator;)"
            + "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;")
    )
    private NaturalSpawner.SpawnState aerogel$prepareNaturalSpawnState(
        int spawnableChunks,
        Iterable<Entity> entities,
        NaturalSpawner.ChunkGetter chunkGetter,
        LocalMobCapCalculator localCaps
    ) {
        NaturalSpawner.SpawnState state = AerogelRuntime.prepareNaturalSpawnState(
            level, spawnableChunks, entities, chunkGetter, localCaps);
        aerogel$currentSpawnState = state;
        return state;
    }

    @Inject(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At("RETURN")
    )
    private void aerogel$sealNaturalSpawnWave(
        net.minecraft.util.profiling.ProfilerFiller profiler,
        long inhabitedTimeDelta, CallbackInfo callback
    ) {
        NaturalSpawner.SpawnState state = aerogel$currentSpawnState;
        aerogel$currentSpawnState = null;
        if (state != null) AerogelRuntime.sealNaturalSpawnWave(state);
    }

    @Redirect(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;"
            + "forEachBlockTickingChunk(Ljava/util/function/Consumer;)V")
    )
    private void aerogel$parallelBlockTickingChunks(
        ChunkMap chunkMap, Consumer<LevelChunk> action
    ) {
        AerogelRuntime.tickChunks(level, chunkMap, action);
    }

    @Redirect(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;"
            + "tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;J"
            + "Ljava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V")
    )
    private void aerogel$parallelNaturalSpawning(
        ServerChunkCache cache, LevelChunk chunk, long inhabitedTimeDelta,
        List<MobCategory> categories, NaturalSpawner.SpawnState spawnState
    ) {
        AerogelRuntime.withPreparedNaturalSpawnState(
            spawnState, categories, (prepared, exactCategories) ->
                AerogelRuntime.tickSpawningChunk(level, chunk, spawnState, () ->
                    aerogel$invokeTickSpawningChunk(
                        chunk, inhabitedTimeDelta, exactCategories, prepared)));
    }
}
