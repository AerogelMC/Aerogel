package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.ServerEntityBridge;
import dev.aerogel.loader.internal.ContextOwnedEntityTask;
import dev.aerogel.loader.internal.TrackedEntityBridge;
import dev.aerogel.loader.internal.GenerationNodeExecutorBridge;
import dev.aerogel.loader.worldgen.ChunkLoadAssemblyScope;
import dev.aerogel.loader.worldgen.ChunkGenerationCoordinator;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.context.DenseLongObjectList;
import dev.aerogel.loader.context.ConcurrentLongSet;
import dev.aerogel.loader.context.CommitScope;
import dev.aerogel.loader.context.PublishedChunkHolderIndex;
import dev.aerogel.loader.context.ConcurrentGenerationTaskList;
import dev.aerogel.loader.context.OwnerPublicationBarrier;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.TriState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.entity.EntityAccess;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import dev.aerogel.loader.context.NativeTickCoordinator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.IntSupplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import dev.aerogel.loader.internal.LevelTicksBridge;

@Mixin(targets = "net.minecraft.server.level.ChunkMap")
abstract class ChunkMapMixin implements ChunkMapTrackingBridge, GenerationNodeExecutorBridge {
    @Shadow @Final private static Logger LOGGER;
    private static final ThreadLocal<MoveSnapshot> AEROGEL_MOVE_SNAPSHOT =
        new ThreadLocal<>();
    private static final ThreadLocal<Boolean> AEROGEL_REPLAYING_UNSAVED =
        ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> AEROGEL_REPLAYING_FULL_STATUS =
        ThreadLocal.withInitial(() -> false);
    private static final ObjectCollection<Object> AEROGEL_EMPTY_TRACKED_ENTITIES =
        new ObjectArrayList<>(java.util.List.of());

    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Int2ObjectMap<Object> entityMap;
    @Shadow @Final private ChunkTaskDispatcher worldgenTaskDispatcher;
    @Shadow @Final private ChunkTaskDispatcher lightTaskDispatcher;
    @Shadow @Final private PoiManager poiManager;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
    @Shadow @Final @Mutable private java.util.List<ChunkGenerationTask>
        pendingGenerationTasks;
    @Shadow @Final @Mutable private LongSet chunksToEagerlySave;
    @Shadow public abstract DistanceManager getDistanceManager();
    @Shadow abstract void onFullChunkStatusChange(
        ChunkPos position, FullChunkStatus status);
    @Invoker("playerIsCloseEnoughForSpawning")
    protected abstract boolean aerogel$exactPlayerSpawnDistance(
        ServerPlayer player, ChunkPos position);
    @Invoker("readChunk")
    protected abstract CompletableFuture<Optional<CompoundTag>> aerogel$readChunk(
        ChunkPos position);
    @Invoker("createEmptyChunk")
    protected abstract ChunkAccess aerogel$createEmptyChunk(ChunkPos position);
    @Invoker("handleChunkLoadFailure")
    protected abstract ChunkAccess aerogel$handleChunkLoadFailure(
        Throwable failure, ChunkPos position);
    @Invoker("markPosition")
    protected abstract byte aerogel$markPosition(
        ChunkPos position, net.minecraft.world.level.chunk.status.ChunkType type);
    @Invoker("getChunkQueueLevel")
    protected abstract IntSupplier aerogel$getChunkQueueLevel(long chunkKey);
    @Unique private boolean aerogel$spawnCandidatesInitialized;
    @Unique private final Long2ObjectOpenHashMap<SpawnCandidate>
        aerogel$spawnCandidates = new Long2ObjectOpenHashMap<>();
    @Unique private final Long2ObjectOpenHashMap<LongOpenHashSet>
        aerogel$boundaryCandidates = new Long2ObjectOpenHashMap<>();
    @Unique private final DenseLongObjectList<LevelChunk> aerogel$eligibleSpawnChunks =
        new DenseLongObjectList<>();
    /* Vanilla's exact squared spawning radius is 16384, hence an exact 128-block bucket. */
    @Unique private static final int AEROGEL_SPAWN_BUCKET_WIDTH = 128;
    @Unique private final ConcurrentHashMap<Long, Set<ServerPlayer>> aerogel$spawnPlayers =
        new ConcurrentHashMap<>();
    @Unique private final ConcurrentHashMap<ServerPlayer, Long> aerogel$spawnPlayerBuckets =
        new ConcurrentHashMap<>();
    @Unique private long aerogel$spawnPlayerRefreshEpoch;
    @Unique private final PublishedChunkHolderIndex aerogel$generationHolders =
        new PublishedChunkHolderIndex();
    @Unique private final ChunkGenerationCoordinator aerogel$generationCoordinator =
        new ChunkGenerationCoordinator(
            (net.minecraft.server.level.GeneratingChunkMap) (Object) this, this);

    /**
     * Keeps vanilla disk IO, data fixing and NBT parsing, but replaces the
     * server-thread chunk assembly continuation with owner-keyed stages. The
     * chunk future is exposed only after light and server-owned indexes have
     * observed the assembled result.
     */
    @Inject(method = "scheduleChunkLoad", at = @At("HEAD"), cancellable = true)
    private void aerogel$loadChunkThroughOwnerPipeline(
        ChunkPos position,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback
    ) {
        CompletableFuture<Optional<SerializableChunkData>> parsed =
            aerogel$readChunk(position).thenCompose(
                optional -> aerogel$parseLoadedChunk(position, optional));

        CompletableFuture<?> poiReady = poiManager.prefetch(position);
        CompletableFuture<ChunkAccess> assembled = parsed
            .thenCombine(poiReady, (data, ignored) -> data)
            .thenCompose(data -> aerogel$assembleLoadedChunk(position, data));
        CompletableFuture<ChunkAccess> loaded = assembled.handle((chunk, failure) ->
            failure == null ? CompletableFuture.completedFuture(chunk)
                : aerogel$recoverChunkLoad(position, failure)).thenCompose(future -> future);
        callback.setReturnValue(loaded);
    }

    @Unique
    private CompletableFuture<Optional<SerializableChunkData>> aerogel$parseLoadedChunk(
        ChunkPos position, Optional<CompoundTag> tag
    ) {
        long chunkKey = position.pack();
        CompletableFuture<Optional<SerializableChunkData>> result =
            new CompletableFuture<>();
        try {
            worldgenTaskDispatcher.submit(() -> {
                try {
                    result.complete(tag.map(value -> {
                        SerializableChunkData data = SerializableChunkData.parse(
                            (net.minecraft.world.level.LevelHeightAccessor)
                                (Object) level,
                            level.palettedContainerFactory(), value);
                        if (data == null) {
                            LOGGER.error(
                                "Chunk file at {} is missing level data, skipping",
                                position);
                        }
                        return data;
                    }));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            }, chunkKey, aerogel$getChunkQueueLevel(chunkKey));
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    @Unique
    private CompletableFuture<ChunkAccess> aerogel$recoverChunkLoad(
        ChunkPos position, Throwable failure
    ) {
        CompletableFuture<ChunkAccess> recovered = new CompletableFuture<>();
        NativeTickCoordinator.submitGlobalCommit(() -> {
            try {
                recovered.complete(aerogel$handleChunkLoadFailure(failure, position));
            } catch (Throwable recoveryFailure) {
                recovered.completeExceptionally(recoveryFailure);
            }
        });
        return recovered;
    }

    @Unique
    private CompletableFuture<ChunkAccess> aerogel$assembleLoadedChunk(
        ChunkPos position, Optional<SerializableChunkData> serialized
    ) {
        long chunkKey = position.pack();
        IntSupplier priority = aerogel$getChunkQueueLevel(chunkKey);
        CompletableFuture<ChunkAccess> result = new CompletableFuture<>();

        try {
            worldgenTaskDispatcher.submit(() -> {
                try {
                    ChunkLoadAssemblyScope.Result<ChunkAccess> assembly =
                        ChunkLoadAssemblyScope.capture(() -> serialized
                            .<ChunkAccess>map(data -> data.read(
                                level, poiManager,
                                ((ChunkMap) (Object) this).storageInfo(), position))
                            .orElseGet(() -> aerogel$createEmptyChunk(position)));

                    CompletableFuture<Void> lightPublication =
                        aerogel$publishLoadedLight(
                            chunkKey, priority, assembly.lightPublications());
                    CompletableFuture<Void> serverPublication =
                        aerogel$publishLoadedIndexes(
                            position, serialized, assembly.serverPublications());
                    CompletableFuture.allOf(lightPublication, serverPublication)
                        .whenComplete((ignored, failure) -> {
                            if (failure == null) result.complete(assembly.value());
                            else result.completeExceptionally(failure);
                        });
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            }, chunkKey, priority);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    @Unique
    private CompletableFuture<Void> aerogel$publishLoadedLight(
        long chunkKey, IntSupplier priority, Runnable[] publications
    ) {
        if (publications.length == 0) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            lightTaskDispatcher.submit(() -> {
                try {
                    for (Runnable publication : publications) publication.run();
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            }, chunkKey, priority);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }

    @Unique
    private CompletableFuture<Void> aerogel$publishLoadedIndexes(
        ChunkPos position,
        Optional<SerializableChunkData> serialized,
        Runnable[] publications
    ) {
        if (publications.length == 0 && serialized.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        NativeTickCoordinator.submitGlobalCommit(() -> {
            try {
                for (Runnable publication : publications) publication.run();
                if (serialized.isPresent()) {
                    Profiler.get().incrementCounter("chunkLoad");
                    aerogel$markPosition(position,
                        serialized.orElseThrow().chunkStatus().getChunkType());
                }
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$listenForSpawnDistanceChanges(CallbackInfo callback) {
        // Each key is an independent chunk-owned publication. Vanilla's save
        // pass may enumerate concurrently without sharing a mutable iterator.
        chunksToEagerlySave = new ConcurrentLongSet();
        pendingGenerationTasks = new ConcurrentGenerationTaskList<>();
        ((DistanceManagerBridge) getDistanceManager())
            .aerogel$spawnDistanceListener(this::aerogel$updateSpawnCandidate);
    }

    /**
     * ChunkHolder demotion is prepared by its Chunk Context, but vanilla's
     * listener mutates the world's shared entity-section and tracking indexes.
     * Publish only that exact side effect at the server-owner boundary and make
     * the holder generation wait for its completion.
     */
    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"), cancellable = true)
    private void aerogel$publishFullChunkStatus(
        ChunkPos position, FullChunkStatus status, CallbackInfo callback
    ) {
        if (AEROGEL_REPLAYING_FULL_STATUS.get()) return;
        if (OwnerPublicationBarrier.defer(() -> {
            AEROGEL_REPLAYING_FULL_STATUS.set(true);
            try {
                onFullChunkStatusChange(position, status);
            } finally {
                AEROGEL_REPLAYING_FULL_STATUS.remove();
            }
        })) {
            callback.cancel();
        }
    }

    /**
     * Candidate membership is maintained by distance, full-status and exact
     * player-position events. The per-tick path only drains any newly completed
     * distance generation and copies the already eligible LevelChunk values.
     */
    @Inject(method = "collectSpawningChunks(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$collectPublishedSpawningChunks(
        java.util.List<LevelChunk> output, CallbackInfo callback
    ) {
        DistanceManager manager = getDistanceManager();
        // Preserve vanilla's exact drain point. Published distance changes call
        // aerogel$updateSpawnCandidate through the listener above.
        LongIterator keys = manager.getSpawnCandidateChunks();
        if (!aerogel$spawnCandidatesInitialized) {
            while (keys.hasNext()) {
                aerogel$updateSpawnCandidate(keys.nextLong());
            }
            aerogel$spawnCandidatesInitialized = true;
        }
        aerogel$eligibleSpawnChunks.forEach(output::add);
        callback.cancel();
    }

    @Unique
    private void aerogel$updateSpawnCandidate(long key) {
        SpawnCandidate previous = aerogel$spawnCandidates.remove(key);
        if (previous != null && previous.exactPlayerDistance) {
            aerogel$removeBoundaryCandidate(previous.bucket, key);
        }

        ChunkHolder holder = visibleChunkMap.get(key);
        TriState nearby = ((DistanceManagerBridge) getDistanceManager())
            .aerogel$publishedPlayersNearby(key);
        if (holder == null || nearby == TriState.FALSE) {
            aerogel$eligibleSpawnChunks.remove(key);
            return;
        }

        ChunkPos position = holder.getPos();
        boolean exactPlayerDistance = nearby == TriState.DEFAULT;
        long bucket = aerogel$spawnBucket(
            position.x() * 16.0D + 8.0D, position.z() * 16.0D + 8.0D);
        SpawnCandidate candidate = new SpawnCandidate(
            key, holder, position, exactPlayerDistance, bucket);
        aerogel$spawnCandidates.put(key, candidate);
        if (exactPlayerDistance) {
            aerogel$boundaryCandidates
                .computeIfAbsent(bucket, ignored -> new LongOpenHashSet()).add(key);
            aerogel$initializeExactSpawnPlayers(candidate);
        }
        aerogel$refreshSpawnEligibility(candidate);
    }

    @Unique
    private void aerogel$removeBoundaryCandidate(long bucket, long key) {
        LongOpenHashSet candidates = aerogel$boundaryCandidates.get(bucket);
        if (candidates == null) return;
        candidates.remove(key);
        if (candidates.isEmpty()) aerogel$boundaryCandidates.remove(bucket);
    }

    @Unique
    private void aerogel$refreshSpawnEligibility(SpawnCandidate candidate) {
        LevelChunk chunk = candidate.holder.getTickingChunk();
        if (chunk != null && (!candidate.exactPlayerDistance
            || !candidate.exactPlayers.isEmpty())) {
            aerogel$eligibleSpawnChunks.put(candidate.key, chunk);
        } else {
            aerogel$eligibleSpawnChunks.remove(candidate.key);
        }
    }

    @Unique
    private void aerogel$refreshBoundaryCandidates(
        ServerPlayer player, Long previousBucket, Long nextBucket, boolean tracked
    ) {
        long epoch = ++aerogel$spawnPlayerRefreshEpoch;
        if (previousBucket != null) {
            aerogel$refreshBoundaryBucket(previousBucket, player, tracked, epoch);
        }
        if (nextBucket != null
            && (previousBucket == null || previousBucket.longValue() != nextBucket.longValue())) {
            aerogel$refreshBoundaryBucket(nextBucket, player, tracked, epoch);
        }
    }

    @Unique
    private void aerogel$refreshBoundaryBucket(
        long playerBucket, ServerPlayer player, boolean tracked, long epoch
    ) {
        int bucketX = ChunkPos.getX(playerBucket);
        int bucketZ = ChunkPos.getZ(playerBucket);
        for (int x = bucketX - 1; x <= bucketX + 1; x++) {
            for (int z = bucketZ - 1; z <= bucketZ + 1; z++) {
                LongOpenHashSet candidates = aerogel$boundaryCandidates.get(ChunkPos.pack(x, z));
                if (candidates == null) continue;
                LongIterator keys = candidates.iterator();
                while (keys.hasNext()) {
                    SpawnCandidate candidate = aerogel$spawnCandidates.get(keys.nextLong());
                    if (candidate == null || candidate.playerRefreshEpoch == epoch) continue;
                    candidate.playerRefreshEpoch = epoch;
                    boolean wasNear = candidate.exactPlayers.contains(player);
                    boolean isNear = tracked
                        && aerogel$exactPlayerSpawnDistance(player, candidate.position);
                    if (wasNear == isNear) continue;
                    if (isNear) candidate.exactPlayers.add(player);
                    else candidate.exactPlayers.remove(player);
                    aerogel$refreshSpawnEligibility(candidate);
                }
            }
        }
    }

    @Inject(method = "updatePlayerStatus", at = @At("RETURN"))
    private void aerogel$publishSpawnPlayerStatus(
        ServerPlayer player, boolean tracked, CallbackInfo callback
    ) {
        Long previous = aerogel$spawnPlayerBuckets.get(player);
        if (tracked) {
            aerogel$publishSpawnPlayer(player);
            aerogel$refreshBoundaryCandidates(player, previous,
                aerogel$spawnPlayerBuckets.get(player), true);
        } else {
            aerogel$removeSpawnPlayer(player);
            aerogel$refreshBoundaryCandidates(player, previous, null, false);
        }
    }

    @Inject(method = "move(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void aerogel$publishSpawnPlayerMove(ServerPlayer player, CallbackInfo callback) {
        Long previous = aerogel$spawnPlayerBuckets.get(player);
        if (previous == null) return;
        aerogel$publishSpawnPlayer(player);
        // Even inside one bucket the exact vanilla circle changes continuously.
        aerogel$refreshBoundaryCandidates(
            player, previous, aerogel$spawnPlayerBuckets.get(player), true);
    }

    @Unique
    private void aerogel$publishSpawnPlayer(ServerPlayer player) {
        long next = aerogel$spawnBucket(player.position().x, player.position().z);
        Long previous = aerogel$spawnPlayerBuckets.put(player, next);
        if (previous != null && previous.longValue() == next) return;
        if (previous != null) {
            Set<ServerPlayer> old = aerogel$spawnPlayers.get(previous);
            if (old != null) old.remove(player);
        }
        aerogel$spawnPlayers.computeIfAbsent(next, ignored -> ConcurrentHashMap.newKeySet())
            .add(player);
    }

    @Unique
    private void aerogel$removeSpawnPlayer(ServerPlayer player) {
        Long previous = aerogel$spawnPlayerBuckets.remove(player);
        if (previous == null) return;
        Set<ServerPlayer> old = aerogel$spawnPlayers.get(previous);
        if (old != null) {
            old.remove(player);
            if (old.isEmpty()) aerogel$spawnPlayers.remove(previous, old);
        }
    }

    @Unique
    private void aerogel$initializeExactSpawnPlayers(SpawnCandidate candidate) {
        double centerX = candidate.position.x() * 16.0D + 8.0D;
        double centerZ = candidate.position.z() * 16.0D + 8.0D;
        int bucketX = Math.floorDiv((int) Math.floor(centerX), AEROGEL_SPAWN_BUCKET_WIDTH);
        int bucketZ = Math.floorDiv((int) Math.floor(centerZ), AEROGEL_SPAWN_BUCKET_WIDTH);
        // A circle whose radius equals one bucket width can intersect only these 3x3 buckets.
        for (int x = bucketX - 1; x <= bucketX + 1; x++) {
            for (int z = bucketZ - 1; z <= bucketZ + 1; z++) {
                Set<ServerPlayer> players = aerogel$spawnPlayers.get(ChunkPos.pack(x, z));
                if (players == null) continue;
                for (ServerPlayer player : players) {
                    if (aerogel$exactPlayerSpawnDistance(player, candidate.position)) {
                        candidate.exactPlayers.add(player);
                    }
                }
            }
        }
    }

    @Unique
    private static long aerogel$spawnBucket(double x, double z) {
        return ChunkPos.pack(
            Math.floorDiv((int) Math.floor(x), AEROGEL_SPAWN_BUCKET_WIDTH),
            Math.floorDiv((int) Math.floor(z), AEROGEL_SPAWN_BUCKET_WIDTH));
    }

    @Inject(method = "onFullChunkStatusChange", at = @At("RETURN"))
    private void aerogel$wakeScheduledTicks(
        ChunkPos position, @Coerce Object status,
        CallbackInfo callback
    ) {
        long key = position.pack();
        aerogel$updateSpawnCandidate(key);
        ((LevelTicksBridge) (Object) level.getBlockTicks())
            .aerogel$eligibilityChanged(key);
        ((LevelTicksBridge) (Object) level.getFluidTicks())
            .aerogel$eligibilityChanged(key);
    }

    @Unique
    private static final class SpawnCandidate {
        private final long key;
        private final ChunkHolder holder;
        private final ChunkPos position;
        private final boolean exactPlayerDistance;
        private final long bucket;
        private final Set<ServerPlayer> exactPlayers;
        private long playerRefreshEpoch;

        private SpawnCandidate(
            long key, ChunkHolder holder, ChunkPos position,
            boolean exactPlayerDistance, long bucket
        ) {
            this.key = key;
            this.holder = holder;
            this.position = position;
            this.exactPlayerDistance = exactPlayerDistance;
            this.bucket = bucket;
            this.exactPlayers = exactPlayerDistance ? new HashSet<>() : Set.of();
        }
    }

    @Inject(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/"
            + "Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;",
            ordinal = 0),
        cancellable = true
    )
    private void aerogel$tickPersistentTrackingIndex(CallbackInfo callback) {
        AerogelRuntime.tickTrackedEntities(level, level.players());
        callback.cancel();
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void aerogel$registerTrackedEntity(Entity entity, CallbackInfo callback) {
        Object tracked = entityMap.get(entity.getId());
        if (tracked instanceof TrackedEntityBridge bridge) {
            AerogelRuntime.registerTrackedEntity(level, entity, bridge);
        }
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void aerogel$unregisterTrackedEntity(Entity entity, CallbackInfo callback) {
        AerogelRuntime.unregisterTrackedEntity(entity);
    }

    @Invoker("setChunkUnsaved")
    protected abstract void aerogel$setChunkUnsaved(ChunkPos position);

    @Override
    public ServerLevel aerogel$level() {
        return level;
    }

    @Override
    public Object aerogel$trackedEntity(int entityId) {
        return entityMap.get(entityId);
    }

    @Override
    public void aerogel$publishGenerationHolders(
        long[] chunkKeys, ChunkHolder[] holders
    ) {
        aerogel$generationHolders.publish(chunkKeys, holders);
    }

    @Inject(method = "acquireGeneration", at = @At("HEAD"), cancellable = true)
    private void aerogel$acquirePublishedGenerationHolder(
        long chunkKey, CallbackInfoReturnable<GenerationChunkHolder> callback
    ) {
        if (level.getServer().isSameThread()) return;
        ChunkHolder holder = aerogel$generationHolders.get(chunkKey);
        if (holder == null) {
            throw new IllegalStateException(
                "No published generation holder for " + ChunkPos.unpack(chunkKey));
        }
        holder.increaseGenerationRefCount();
        callback.setReturnValue(holder);
    }

    @Override
    public void aerogel$submitGenerationNode(
        GenerationChunkHolder holder, Runnable task
    ) {
        worldgenTaskDispatcher.submit(
            task, holder.getPos().pack(), holder::getQueueLevel);
    }

    @Override
    public ChunkGenerationCoordinator aerogel$generationCoordinator() {
        return aerogel$generationCoordinator;
    }

    @Override
    public void aerogel$moveSnapshot(
        ServerPlayer player, SectionPos section, ChunkPos chunk
    ) {
        if (AEROGEL_MOVE_SNAPSHOT.get() != null) {
            throw new IllegalStateException("Nested player movement snapshot");
        }
        AEROGEL_MOVE_SNAPSHOT.set(new MoveSnapshot(player, section, chunk));
        try {
            ((ChunkMap) (Object) this).move(player);
        } finally {
            AEROGEL_MOVE_SNAPSHOT.remove();
        }
    }

    @Redirect(
        method = {
            "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            "updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)V"
        },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;"
            + "of(Lnet/minecraft/world/level/entity/EntityAccess;)"
            + "Lnet/minecraft/core/SectionPos;")
    )
    private SectionPos aerogel$coherentMoveSection(EntityAccess entity) {
        MoveSnapshot snapshot = AEROGEL_MOVE_SNAPSHOT.get();
        return snapshot != null && snapshot.player() == entity
            ? snapshot.section()
            : SectionPos.of(entity);
    }

    @Redirect(
        method = "updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;"
            + "chunkPosition()Lnet/minecraft/world/level/ChunkPos;")
    )
    private ChunkPos aerogel$coherentMoveChunk(ServerPlayer player) {
        MoveSnapshot snapshot = AEROGEL_MOVE_SNAPSHOT.get();
        return snapshot != null && snapshot.player() == player
            ? snapshot.chunk()
            : player.chunkPosition();
    }

    /**
     * Player visibility against every tracked entity is already distributed by
     * ContextServiceImpl using the same section-change snapshot. Keep only the
     * player-distance and chunk-view half of vanilla's move transaction here.
     */
    @Redirect(
        method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/"
            + "Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;")
    )
    private ObjectCollection<Object> aerogel$distributedPlayerTracking(
        Int2ObjectMap<Object> entities
    ) {
        return AEROGEL_MOVE_SNAPSHOT.get() == null
            ? entities.values()
            : AEROGEL_EMPTY_TRACKED_ENTITIES;
    }

    @Redirect(
        method = "forEachBlockTickingChunk(Ljava/util/function/Consumer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;"
            + "forEachEntityTickingChunk(Lit/unimi/dsi/fastutil/longs/LongConsumer;)V")
    )
    private void aerogel$publishedTickingChunks(
        @Coerce Object manager, LongConsumer consumer
    ) {
        ((DistanceManagerBridge) manager)
            .aerogel$forEachPublishedEntityTickingChunk(consumer);
    }

    @Inject(method = "setChunkUnsaved(Lnet/minecraft/world/level/ChunkPos;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$commitUnsavedChunk(ChunkPos position, CallbackInfo callback) {
        if (AEROGEL_REPLAYING_UNSAVED.get()) return;
        if (!NativeTickCoordinator.isNativeWorker()) return;
        if (NativeTickCoordinator.deferCommit(
            CommitScope.CONTEXT, () -> {
                AEROGEL_REPLAYING_UNSAVED.set(true);
                try {
                    aerogel$setChunkUnsaved(position);
                } finally {
                    AEROGEL_REPLAYING_UNSAVED.remove();
                }
            })) {
            callback.cancel();
        }
    }

    private record MoveSnapshot(
        ServerPlayer player, SectionPos section, ChunkPos chunk
    ) { }

}
