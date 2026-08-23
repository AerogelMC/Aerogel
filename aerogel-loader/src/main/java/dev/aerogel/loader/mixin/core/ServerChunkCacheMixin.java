package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import dev.aerogel.loader.internal.DistanceManagerBridge;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    @Unique private final AtomicBoolean aerogel$distanceUpdateQueued = new AtomicBoolean();
    @Unique private boolean aerogel$distanceUpdateRunning;
    @Unique private boolean aerogel$distanceUpdateRequested;
    @Shadow public abstract ChunkAccess getChunk(
        int chunkX, int chunkZ, ChunkStatus targetStatus, boolean create);

    @Invoker("getVisibleChunkIfPresent")
    protected abstract ChunkHolder aerogel$getVisibleChunk(long packedPosition);

    @Invoker("tickSpawningChunk")
    protected abstract void aerogel$invokeTickSpawningChunk(
        LevelChunk chunk, long inhabitedTimeDelta, List<MobCategory> categories,
        NaturalSpawner.SpawnState spawnState);

    @Invoker("getChunkFutureMainThread")
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>>
        aerogel$getChunkFutureMainThread(
            int chunkX, int chunkZ, ChunkStatus targetStatus, boolean create);

    @Invoker("runDistanceManagerUpdates")
    protected abstract boolean aerogel$runDistanceManagerUpdates();

    @Invoker("addTicketAndLoadWithRadius")
    protected abstract CompletableFuture<?> aerogel$addTicketAndLoadWithRadius(
        TicketType type, ChunkPos position, int radius);

    @Invoker("addTicket")
    protected abstract void aerogel$addTicket(Ticket ticket, ChunkPos position);

    @Invoker("addTicketWithRadius")
    protected abstract void aerogel$addTicketWithRadius(
        TicketType type, ChunkPos position, int radius);

    @Invoker("removeTicketWithRadius")
    protected abstract void aerogel$removeTicketWithRadius(
        TicketType type, ChunkPos position, int radius);

    /**
     * DistanceManager owns several fastutil collections that vanilla deliberately
     * leaves non-concurrent, including chunksToUpdateFutures. Keep their mutation
     * on the ServerChunkCache owner thread. A foreign caller publishes one
     * coalesced request; it never enters the distance graph itself.
     */
    @Inject(method = "runDistanceManagerUpdates", at = @At("HEAD"), cancellable = true)
    private void aerogel$ownDistanceManagerUpdates(
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (Thread.currentThread() != mainThread) {
            if (aerogel$distanceUpdateQueued.compareAndSet(false, true)) {
                NativeTickCoordinator.submitGlobalCommit(() -> {
                    aerogel$distanceUpdateQueued.set(false);
                    aerogel$runDistanceManagerUpdates();
                });
            }
            callback.setReturnValue(false);
            return;
        }
        if (aerogel$distanceUpdateRunning) {
            aerogel$distanceUpdateRequested = true;
            callback.setReturnValue(false);
            return;
        }
        aerogel$distanceUpdateRunning = true;
    }

    @Inject(method = "runDistanceManagerUpdates", at = @At("RETURN"))
    private void aerogel$finishDistanceManagerUpdates(
        CallbackInfoReturnable<Boolean> callback
    ) {
        aerogel$distanceUpdateRunning = false;
        if (!aerogel$distanceUpdateRequested) return;
        aerogel$distanceUpdateRequested = false;
        boolean followUpChangedChunks = aerogel$runDistanceManagerUpdates();
        callback.setReturnValue(callback.getReturnValueZ() || followUpChangedChunks);
    }

    @Inject(
        method = "addTicketAndLoadWithRadius(Lnet/minecraft/server/level/TicketType;"
            + "Lnet/minecraft/world/level/ChunkPos;I)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$ownTicketLoadTransaction(
        TicketType type, ChunkPos position, int radius,
        CallbackInfoReturnable<CompletableFuture<?>> callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        CompletableFuture<Object> published = new CompletableFuture<>();
        NativeTickCoordinator.submitGlobalCommit(() -> {
            try {
                aerogel$addTicketAndLoadWithRadius(type, position, radius)
                    .whenComplete((result, error) -> {
                        if (error == null) published.complete(result);
                        else published.completeExceptionally(error);
                    });
            } catch (Throwable error) {
                published.completeExceptionally(error);
            }
        });
        callback.setReturnValue(published);
    }

    @Inject(
        method = "addTicket(Lnet/minecraft/server/level/Ticket;"
            + "Lnet/minecraft/world/level/ChunkPos;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$ownTicketAdd(
        Ticket ticket, ChunkPos position, CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        NativeTickCoordinator.submitGlobalCommit(() ->
            aerogel$addTicket(ticket, position));
        callback.cancel();
    }

    @Inject(
        method = "addTicketWithRadius(Lnet/minecraft/server/level/TicketType;"
            + "Lnet/minecraft/world/level/ChunkPos;I)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$ownRadiusTicketAdd(
        TicketType type, ChunkPos position, int radius, CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        NativeTickCoordinator.submitGlobalCommit(() ->
            aerogel$addTicketWithRadius(type, position, radius));
        callback.cancel();
    }

    @Inject(
        method = "removeTicketWithRadius(Lnet/minecraft/server/level/TicketType;"
            + "Lnet/minecraft/world/level/ChunkPos;I)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$ownRadiusTicketRemoval(
        TicketType type, ChunkPos position, int radius, CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        NativeTickCoordinator.submitGlobalCommit(() ->
            aerogel$removeTicketWithRadius(type, position, radius));
        callback.cancel();
    }

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

        ChunkResult<ChunkAccess> result = aerogel$loadNativeChunk(
            chunkX, chunkZ, targetStatus);
        ChunkAccess loaded = result.orElse(null);
        if (loaded == null) {
            throw new IllegalStateException(
                "Chunk generation did not produce " + chunkX + "," + chunkZ
                    + " at status " + targetStatus + ": " + result.getError());
        }
        callback.setReturnValue(loaded);
    }

    /**
     * Pins precisely the requested status while a native transaction consumes it.
     * Vanilla's UNKNOWN ticket expires after one tick because the server thread uses
     * managedBlock; a Context worker has a different lifetime and therefore owns an
     * explicit request lease rather than an arbitrary timeout.
     */
    @Unique
    private ChunkResult<ChunkAccess> aerogel$loadNativeChunk(
        int chunkX, int chunkZ, ChunkStatus targetStatus
    ) {
        ChunkPos position = new ChunkPos(chunkX, chunkZ);
        // TicketStorage de-duplicates by TicketType identity and level. A distinct
        // type per in-flight request makes each lease independently removable.
        TicketType leaseType = new TicketType(
            TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
        Ticket lease = new Ticket(leaseType, ChunkLevel.byStatus(targetStatus));
        TicketStorage tickets = ((DistanceManagerBridge)
            chunkMap.getDistanceManager()).aerogel$ticketStorage();
        CompletableFuture<ChunkResult<ChunkAccess>> loaded = new CompletableFuture<>();

        NativeTickCoordinator.submitGlobalCommit(() -> {
            try {
                tickets.addTicket(lease, position);
                aerogel$runDistanceManagerUpdates();
                aerogel$getChunkFutureMainThread(
                    chunkX, chunkZ, targetStatus, false
                ).whenComplete((result, error) -> {
                    if (error == null) loaded.complete(result);
                    else loaded.completeExceptionally(error);
                });
            } catch (Throwable error) {
                loaded.completeExceptionally(error);
            }
        });

        try {
            return loaded.join();
        } finally {
            Runnable release = () -> NativeTickCoordinator.submitGlobalCommit(() -> {
                tickets.removeTicket(lease, position);
                aerogel$runDistanceManagerUpdates();
            });
            if (!NativeTickCoordinator.deferNativeCompletion(release)) release.run();
        }
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
