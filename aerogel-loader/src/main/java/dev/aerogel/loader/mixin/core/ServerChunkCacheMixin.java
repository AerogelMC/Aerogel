package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.ExactChunkDistanceGraph;
import dev.aerogel.loader.context.ContextDispatchingRandomSource;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.DistanceManager;
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
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.RandomSource;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.entity.Entity;

@Mixin(targets = "net.minecraft.server.level.ServerChunkCache")
abstract class ServerChunkCacheMixin {
    @Unique private static final long AEROGEL_NATIVE_CHUNK_TIMEOUT_SECONDS = 10L;
    @Shadow @Final private Thread mainThread;
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private ChunkMap chunkMap;
    @Shadow @Final private DistanceManager distanceManager;
    @Unique private NaturalSpawner.SpawnState aerogel$currentSpawnState;
    @Unique private final AtomicBoolean aerogel$distanceUpdateQueued = new AtomicBoolean();
    @Unique private boolean aerogel$distanceUpdateRunning;
    @Unique private boolean aerogel$distanceUpdateRequested;
    @Unique private final List<Runnable> aerogel$afterDistanceUpdates =
        new ArrayList<>();
    @Unique private final AtomicBoolean aerogel$distancePublicationFollowUp =
        new AtomicBoolean();
    @Unique private final ConcurrentLinkedQueue<DistancePublication>
        aerogel$distancePublications = new ConcurrentLinkedQueue<>();
    @Unique private final AtomicBoolean aerogel$distancePublicationRunning =
        new AtomicBoolean();
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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$registerMainServerBeforeInitialChunkLoads(
        CallbackInfo callback
    ) {
        // Initial-spawn discovery performs synchronous chunk requests before the
        // first tickServer invocation. Register here so an asynchronous distance
        // generation can wake the server task pump during that bootstrap phase.
        NativeTickCoordinator.registerMainServer(level.getServer());
        ((DistanceManagerBridge) distanceManager)
            .aerogel$bindLoadingGenerationPublisher(
                this::aerogel$queueDistancePublication);
    }

    @Unique
    private CompletableFuture<Void> aerogel$queueDistancePublication(
        ExactChunkDistanceGraph.ChangeBatch changes
    ) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        NativeTickCoordinator.beginAsynchronousWork(
            NativeTickCoordinator.AsynchronousOwner.DISTANCE_PUBLICATION);
        aerogel$distancePublications.offer(
            new DistancePublication(changes, completion));
        aerogel$scheduleDistancePublication();
        return completion;
    }

    @Unique
    private void aerogel$scheduleDistancePublication() {
        if (!aerogel$distancePublicationRunning.compareAndSet(false, true)) return;
        aerogel$startNextDistancePublication();
    }

    @Unique
    private void aerogel$startNextDistancePublication() {
        DistancePublication publication = aerogel$distancePublications.poll();
        if (publication == null) {
            aerogel$distancePublicationRunning.set(false);
            if (!aerogel$distancePublications.isEmpty()) {
                aerogel$scheduleDistancePublication();
            }
            return;
        }
        DistanceManagerBridge distance = (DistanceManagerBridge) distanceManager;
        distance.aerogel$mainThreadExecutor().execute(() -> {
            try {
                DistanceManagerBridge.LoadingGeneration generation =
                    distance.aerogel$applyLoadingGeneration(
                    publication.changes, chunkMap);
                CompletableFuture.runAsync(() ->
                    ((ChunkMapTrackingBridge) chunkMap)
                        .aerogel$publishGenerationHolders(
                            generation.chunkKeys(), generation.publishedHolders()),
                    AerogelRuntime::submitContextComputation
                ).thenCompose(ignored -> AerogelRuntime.runChunkHolderPhases(
                        level,
                        generation.holders(),
                        holder -> distance.aerogel$updateHighestAllowedStatus(
                            holder, chunkMap),
                        holder -> distance.aerogel$updateHolderFutures(holder, chunkMap)
                    )).whenComplete((ignored, error) ->
                        aerogel$finishDistancePublication(publication, error));
            } catch (Throwable error) {
                aerogel$finishDistancePublication(publication, error);
            }
        });
    }

    @Unique
    private void aerogel$finishDistancePublication(
        DistancePublication publication, Throwable error
    ) {
        try {
            if (error == null) publication.completion.complete(null);
            else publication.completion.completeExceptionally(error);
        } finally {
            NativeTickCoordinator.endAsynchronousWork(
                NativeTickCoordinator.AsynchronousOwner.DISTANCE_PUBLICATION);
            aerogel$startNextDistancePublication();
        }
    }

    @Unique
    private record DistancePublication(
        ExactChunkDistanceGraph.ChangeBatch changes,
        CompletableFuture<Void> completion
    ) { }

    /**
     * @author Aerogel
     * @reason Preserve vanilla's ticket and holder semantics while allowing the
     * exact distance graph to publish asynchronously. The method itself never
     * waits: callers that requested a future receive it immediately, while
     * vanilla's synchronous getChunk caller retains its own managedBlock.
     */
    @Overwrite
    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(
        int chunkX, int chunkZ, ChunkStatus targetStatus, boolean create
    ) {
        ChunkPos position = new ChunkPos(chunkX, chunkZ);
        long key = position.pack();
        int requiredLevel = ChunkLevel.byStatus(targetStatus);
        ChunkHolder holder = aerogel$getVisibleChunk(key);

        if (create) {
            aerogel$addTicket(new Ticket(TicketType.UNKNOWN, requiredLevel), position);
            if (aerogel$chunkAbsent(holder, requiredLevel)) {
                ProfilerFiller profiler = Profiler.get();
                profiler.push("chunkLoad");
                try {
                    aerogel$runDistanceManagerUpdates();
                } finally {
                    profiler.pop();
                }

                CompletableFuture<Void> publication =
                    ((DistanceManagerBridge) distanceManager)
                        .aerogel$loadingDistancePublication();
                if (!publication.isDone()) {
                    return publication.thenComposeAsync(ignored ->
                        aerogel$schedulePublishedChunk(key, requiredLevel, targetStatus),
                        ((DistanceManagerBridge) distanceManager)
                            .aerogel$mainThreadExecutor());
                }
                publication.join();
                return aerogel$schedulePublishedChunk(
                    key, requiredLevel, targetStatus);
            }
        }

        if (aerogel$chunkAbsent(holder, requiredLevel)) {
            return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
        }
        return ((GenerationChunkHolderInvoker) (Object) holder)
            .aerogel$scheduleChunkGenerationTask(targetStatus, chunkMap);
    }

    @Unique
    private CompletableFuture<ChunkResult<ChunkAccess>> aerogel$schedulePublishedChunk(
        long key, int requiredLevel, ChunkStatus targetStatus
    ) {
        if (aerogel$distanceUpdateRunning) {
            CompletableFuture<Void> settled = new CompletableFuture<>();
            aerogel$afterDistanceUpdates.add(() -> settled.complete(null));
            aerogel$distanceUpdateRequested = true;
            return settled.thenCompose(ignored ->
                aerogel$schedulePublishedChunk(key, requiredLevel, targetStatus));
        }
        aerogel$runDistanceManagerUpdates();
        ChunkHolder holder = aerogel$getVisibleChunk(key);
        if (aerogel$chunkAbsent(holder, requiredLevel)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "No chunk holder after ticket distance publication"));
        }
        return ((GenerationChunkHolderInvoker) (Object) holder)
            .aerogel$scheduleChunkGenerationTask(targetStatus, chunkMap);
    }

    @Unique
    private boolean aerogel$chunkAbsent(ChunkHolder holder, int requiredLevel) {
        return holder == null
            || ((GenerationChunkHolderInvoker) (Object) holder)
                .aerogel$getTicketLevel() > requiredLevel;
    }

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
        // ServerChunkCache's synchronous getChunk path pumps its private
        // MainThreadExecutor rather than MinecraftServer's task queue. Treat
        // distance publication as the same owner boundary so an asynchronously
        // completed exact-distance generation cannot remain stranded in the
        // global commit queue during initial-spawn discovery.
        NativeTickCoordinator.pumpMainThread();
    }

    @Inject(
        method = "runDistanceManagerUpdates",
        at = @At("RETURN"),
        cancellable = true
    )
    private void aerogel$finishDistanceManagerUpdates(
        CallbackInfoReturnable<Boolean> callback
    ) {
        aerogel$distanceUpdateRunning = false;
        if (aerogel$distanceUpdateRequested) {
            aerogel$distanceUpdateRequested = false;
            boolean followUpChangedChunks = aerogel$runDistanceManagerUpdates();
            callback.setReturnValue(callback.getReturnValueZ() || followUpChangedChunks);
        }
        if (!aerogel$distanceUpdateRunning && !aerogel$afterDistanceUpdates.isEmpty()) {
            Runnable[] completions =
                aerogel$afterDistanceUpdates.toArray(Runnable[]::new);
            aerogel$afterDistanceUpdates.clear();
            for (Runnable completion : completions) completion.run();
        }
        aerogel$scheduleDistancePublicationFollowUp();
    }

    @Unique
    private void aerogel$scheduleDistancePublicationFollowUp() {
        CompletableFuture<Void> publication = ((DistanceManagerBridge) distanceManager)
            .aerogel$loadingDistancePublication();
        if (publication.isDone()
            || !aerogel$distancePublicationFollowUp.compareAndSet(false, true)) return;
        publication.whenComplete((ignored, error) ->
            NativeTickCoordinator.submitGlobalCommit(() -> {
                aerogel$distancePublicationFollowUp.set(false);
                if (error == null) aerogel$runDistanceManagerUpdates();
            }));
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
        if (NativeTickCoordinator.isNativeWorker()) {
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
            return;
        }

        if (!type.doesLoad()) {
            throw new IllegalStateException("Ticket type does not load chunks: " + type);
        }
        if (type.canExpireIfUnloaded()) {
            throw new IllegalStateException("Ticket type can expire while unloaded: " + type);
        }
        aerogel$addTicketWithRadius(type, position, radius);
        aerogel$runDistanceManagerUpdates();
        CompletableFuture<Void> publication = ((DistanceManagerBridge) distanceManager)
            .aerogel$loadingDistancePublication();
        callback.setReturnValue(publication.thenComposeAsync(ignored -> {
            aerogel$runDistanceManagerUpdates();
            ChunkHolder holder = Objects.requireNonNull(
                aerogel$getVisibleChunk(position.pack()),
                "No chunk was scheduled for loading after distance publication");
            return chunkMap.getChunkRangeFuture(holder, radius,
                unused -> ChunkStatus.FULL);
        }, ((DistanceManagerBridge) distanceManager).aerogel$mainThreadExecutor()));
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
        DistanceManagerBridge distance = (DistanceManagerBridge)
            chunkMap.getDistanceManager();
        TicketStorage tickets = distance.aerogel$ticketStorage();
        CompletableFuture<ChunkResult<ChunkAccess>> loaded = new CompletableFuture<>();

        NativeTickCoordinator.submitGlobalCommit(() -> {
            try {
                tickets.addTicket(lease, position);
                aerogel$runDistanceManagerUpdates();
                CompletableFuture<Void> publication =
                    distance.aerogel$loadingDistancePublication();
                if (publication.isDone()) {
                    publication.join();
                    aerogel$requestNativeChunkAfterDistancePublication(
                        position, targetStatus, tickets, loaded);
                } else {
                    publication.whenComplete((ignored, publicationError) -> {
                        if (publicationError != null) {
                            loaded.completeExceptionally(publicationError);
                            return;
                        }
                        NativeTickCoordinator.submitGlobalCommit(() ->
                            aerogel$requestNativeChunkAfterDistancePublication(
                                position, targetStatus, tickets, loaded));
                    });
                }
            } catch (Throwable error) {
                loaded.completeExceptionally(error);
            }
        });

        try {
            return loaded.orTimeout(
                AEROGEL_NATIVE_CHUNK_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof TimeoutException) {
                NativeTickCoordinator.lockCurrentContextAfterTimeout(
                    "chunk load " + position + " at status " + targetStatus,
                    AEROGEL_NATIVE_CHUNK_TIMEOUT_SECONDS);
            }
            throw failure;
        } finally {
            Runnable release = () -> NativeTickCoordinator.submitGlobalCommit(() -> {
                tickets.removeTicket(lease, position);
                aerogel$runDistanceManagerUpdates();
            });
            if (!NativeTickCoordinator.deferNativeCompletion(release)) release.run();
        }
    }

    @Unique
    private void aerogel$requestNativeChunkAfterDistancePublication(
        ChunkPos position, ChunkStatus targetStatus, TicketStorage tickets,
        CompletableFuture<ChunkResult<ChunkAccess>> loaded
    ) {
        try {
            // The explicit request lease already owns the exact generation
            // level. Publish the dependent holder state, then schedule on that
            // holder directly. Calling vanilla getChunkFutureMainThread with
            // create=true here would add a second, one-tick UNKNOWN ticket and
            // start another asynchronous distance wave that this request does
            // not own.
            aerogel$runDistanceManagerUpdates();
            long key = position.pack();
            int requiredLevel = ChunkLevel.byStatus(targetStatus);
            int publishedTicketLevel = tickets.getTicketLevelAt(key, false);
            ChunkHolder holder = aerogel$getVisibleChunk(key);
            GenerationChunkHolderInvoker holderInvoker = holder == null ? null
                : (GenerationChunkHolderInvoker) (Object) holder;
            int holderLevel = holderInvoker == null
                ? Integer.MAX_VALUE : holderInvoker.aerogel$getTicketLevel();
            if (holder == null || holderLevel > requiredLevel) {
                throw new IllegalStateException(
                    "Native chunk lease was published without an eligible holder at "
                        + position + " (requiredLevel=" + requiredLevel
                        + ", ticketStorageLevel=" + publishedTicketLevel
                        + ", holderLevel="
                        + (holder == null ? "missing" : holderLevel) + ")");
            }

            int scheduledHolderLevel = holderLevel;
            CompletableFuture<ChunkResult<ChunkAccess>> generation =
                holderInvoker.aerogel$scheduleChunkGenerationTask(targetStatus, chunkMap);
            boolean completedAtSubmission = generation.isDone();
            generation.whenComplete((result, error) -> {
                if (error != null) {
                    loaded.completeExceptionally(error);
                    return;
                }
                if (result.orElse(null) != null) {
                    loaded.complete(result);
                    return;
                }
                ChunkHolder current = aerogel$getVisibleChunk(key);
                loaded.complete(ChunkResult.error(
                    result.getError() + " (position=" + position
                        + ", requiredLevel=" + requiredLevel
                        + ", completedAtSubmission=" + completedAtSubmission
                        + ", scheduledHolderLevel=" + scheduledHolderLevel
                        + ", currentHolderLevel="
                        + (current == null ? "missing"
                            : ((GenerationChunkHolderInvoker) (Object) current)
                                .aerogel$getTicketLevel())
                        + ", currentTicketStorageLevel="
                        + tickets.getTicketLevelAt(key, false) + ")"));
            });
        } catch (Throwable error) {
            loaded.completeExceptionally(error);
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
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;"
            + "getAllEntities()Ljava/lang/Iterable;")
    )
    private Iterable<Entity> aerogel$deferNaturalSpawnEntitySnapshot(
        ServerLevel level
    ) {
        // The createState redirect below deliberately prepares the exact entity
        // image after the preceding Context wave completes. Java evaluates the
        // original argument before entering that redirect, so allowing this call
        // to proceed would build and immediately discard a second full snapshot.
        return List.of();
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

    @Redirect(
        method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;"
            + "shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V")
    )
    private static <T> void aerogel$shuffleWithStableOwner(
        List<T> values, RandomSource random
    ) {
        RandomSource stable = random instanceof ContextDispatchingRandomSource routed
            ? routed.snapshotDelegate() : random;
        // Exact Fisher-Yates operation used by vanilla Util.shuffle, with the
        // selected owner captured once for the duration of this operation.
        for (int remaining = values.size(); remaining > 1; remaining--) {
            int selected = stable.nextInt(remaining);
            values.set(remaining - 1, values.set(selected, values.get(remaining - 1)));
        }
    }
}
