package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.ServerEntityBridge;
import dev.aerogel.loader.internal.ContextOwnedEntityTask;
import dev.aerogel.loader.internal.TrackedEntityBridge;
import dev.aerogel.loader.internal.GenerationNodeExecutorBridge;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.context.PaddedAtomicLong;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
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
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.TriState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityAccess;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import dev.aerogel.loader.context.NativeTickCoordinator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import dev.aerogel.loader.internal.LevelTicksBridge;

@Mixin(targets = "net.minecraft.server.level.ChunkMap")
abstract class ChunkMapMixin implements ChunkMapTrackingBridge, GenerationNodeExecutorBridge {
    private static final ThreadLocal<MoveSnapshot> AEROGEL_MOVE_SNAPSHOT =
        new ThreadLocal<>();
    private static final ObjectCollection<Object> AEROGEL_EMPTY_TRACKED_ENTITIES =
        new ObjectArrayList<>(java.util.List.of());

    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Int2ObjectMap<Object> entityMap;
    @Shadow @Final private ChunkTaskDispatcher worldgenTaskDispatcher;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
    @Shadow public abstract DistanceManager getDistanceManager();
    @Invoker("playerIsCloseEnoughForSpawning")
    protected abstract boolean aerogel$exactPlayerSpawnDistance(
        ServerPlayer player, ChunkPos position);
    private static final SpawnCandidate[] AEROGEL_NO_SPAWN_CANDIDATES =
        new SpawnCandidate[0];
    @Unique private final PaddedAtomicLong aerogel$fullStatusVersion =
        new PaddedAtomicLong();
    @Unique private long aerogel$cachedSpawnDistanceVersion = Long.MIN_VALUE;
    @Unique private long aerogel$cachedFullStatusVersion = Long.MIN_VALUE;
    @Unique private SpawnCandidate[] aerogel$spawnCandidates =
        AEROGEL_NO_SPAWN_CANDIDATES;
    /* Vanilla's exact squared spawning radius is 16384, hence an exact 128-block bucket. */
    @Unique private static final int AEROGEL_SPAWN_BUCKET_WIDTH = 128;
    @Unique private final ConcurrentHashMap<Long, Set<ServerPlayer>> aerogel$spawnPlayers =
        new ConcurrentHashMap<>();
    @Unique private final ConcurrentHashMap<ServerPlayer, Long> aerogel$spawnPlayerBuckets =
        new ConcurrentHashMap<>();

    /**
     * Vanilla already maintains the exact natural-spawn distance field as
     * players move. collectSpawningChunks drains that field before obtaining
     * its candidate iterator, so read the same published snapshot directly for
     * the unambiguous inner/outer area. Perform the original Euclidean player
     * scan only on the boundary where the square distance cannot decide.
     */
    @Inject(method = "collectSpawningChunks(Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$collectPublishedSpawningChunks(
        java.util.List<LevelChunk> output, CallbackInfo callback
    ) {
        DistanceManager manager = getDistanceManager();
        DistanceManagerBridge published = (DistanceManagerBridge) manager;
        // This call performs the single vanilla distance-field drain required
        // before observing both the iterator and its publication version.
        LongIterator keys = manager.getSpawnCandidateChunks();
        long distanceVersion = published.aerogel$spawnDistanceVersion();
        long statusVersion = aerogel$fullStatusVersion.get();
        if (distanceVersion != aerogel$cachedSpawnDistanceVersion
            || statusVersion != aerogel$cachedFullStatusVersion) {
            ArrayList<SpawnCandidate> rebuilt = new ArrayList<>();
            while (keys.hasNext()) {
                long key = keys.nextLong();
                ChunkHolder holder = visibleChunkMap.get(key);
                if (holder == null) continue;
                TriState nearby = published.aerogel$publishedPlayersNearby(key);
                if (nearby != TriState.FALSE) {
                    rebuilt.add(new SpawnCandidate(
                        holder, holder.getPos(), nearby == TriState.DEFAULT));
                }
            }
            aerogel$spawnCandidates = rebuilt.toArray(AEROGEL_NO_SPAWN_CANDIDATES);
            aerogel$cachedSpawnDistanceVersion = distanceVersion;
            aerogel$cachedFullStatusVersion = statusVersion;
        }

        for (SpawnCandidate candidate : aerogel$spawnCandidates) {
            LevelChunk chunk = candidate.holder.getTickingChunk();
            if (chunk != null && (!candidate.exactPlayerDistance
                || aerogel$hasIndexedPlayerNear(candidate.position))) {
                output.add(chunk);
            }
        }
        callback.cancel();
    }

    @Inject(method = "updatePlayerStatus", at = @At("RETURN"))
    private void aerogel$publishSpawnPlayerStatus(
        ServerPlayer player, boolean tracked, CallbackInfo callback
    ) {
        if (tracked) aerogel$publishSpawnPlayer(player);
        else aerogel$removeSpawnPlayer(player);
    }

    @Inject(method = "move(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void aerogel$publishSpawnPlayerMove(ServerPlayer player, CallbackInfo callback) {
        if (aerogel$spawnPlayerBuckets.containsKey(player)) aerogel$publishSpawnPlayer(player);
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
        if (old != null) old.remove(player);
    }

    @Unique
    private boolean aerogel$hasIndexedPlayerNear(ChunkPos position) {
        double centerX = position.x() * 16.0D + 8.0D;
        double centerZ = position.z() * 16.0D + 8.0D;
        int bucketX = Math.floorDiv((int) Math.floor(centerX), AEROGEL_SPAWN_BUCKET_WIDTH);
        int bucketZ = Math.floorDiv((int) Math.floor(centerZ), AEROGEL_SPAWN_BUCKET_WIDTH);
        // A circle whose radius equals one bucket width can intersect only these 3x3 buckets.
        for (int x = bucketX - 1; x <= bucketX + 1; x++) {
            for (int z = bucketZ - 1; z <= bucketZ + 1; z++) {
                Set<ServerPlayer> players = aerogel$spawnPlayers.get(ChunkPos.pack(x, z));
                if (players == null) continue;
                for (ServerPlayer player : players) {
                    if (aerogel$exactPlayerSpawnDistance(player, position)) return true;
                }
            }
        }
        return false;
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
        aerogel$fullStatusVersion.incrementAndGet();
        long key = position.pack();
        ((LevelTicksBridge) (Object) level.getBlockTicks())
            .aerogel$eligibilityChanged(key);
        ((LevelTicksBridge) (Object) level.getFluidTicks())
            .aerogel$eligibilityChanged(key);
    }

    @Unique
    private record SpawnCandidate(
        ChunkHolder holder, ChunkPos position, boolean exactPlayerDistance
    ) { }

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
    public void aerogel$submitGenerationNode(
        GenerationChunkHolder holder, Runnable task
    ) {
        worldgenTaskDispatcher.submit(
            task, holder.getPos().pack(), holder::getQueueLevel);
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
        if (!NativeTickCoordinator.isNativeWorker()) return;
        if (NativeTickCoordinator.deferGlobalCommit(
            () -> aerogel$setChunkUnsaved(position))) callback.cancel();
    }

    private record MoveSnapshot(
        ServerPlayer player, SectionPos section, ChunkPos chunk
    ) { }

}
