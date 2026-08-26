package dev.aerogel.loader.context;

import dev.aerogel.api.context.ChunkContext;
import dev.aerogel.api.context.ContextService;
import dev.aerogel.api.context.WorldContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Function;
import dev.aerogel.loader.internal.EntityContextOwnerBridge;
import dev.aerogel.loader.internal.EntityContextScopeBridge;
import dev.aerogel.loader.internal.ContextOwnedEntityTask;
import dev.aerogel.loader.internal.TrackedEntityBridge;
import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.PreparedSpawnStateBridge;
import dev.aerogel.loader.internal.LocalMobCapSnapshotBridge;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.gameevent.GameEvent;

public final class ContextServiceImpl implements ContextService, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Aerogel-Contexts");
    private static final int DEFAULT_WORKERS = Math.max(
        2, Runtime.getRuntime().availableProcessors() - 1);

    private final ConcurrentHashMap<ServerLevel, WorldContextImpl> worlds =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Entity, TrackedRegistration> trackedEntities =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkContextImpl,
        ConcurrentHashMap<Entity, TrackedRegistration>> trackedByContext =
        new ConcurrentHashMap<>();
    /**
     * Viewers that have already participated in a tracking pass, partitioned by
     * dimension. A player is added only after ChunkMap has published its initial
     * chunk-tracking view, so the first distributed visibility pass observes the
     * same view that vanilla uses for TrackedEntity.updatePlayer.
     */
    private final ConcurrentHashMap<ServerLevel, Set<ServerPlayer>> trackingViewers =
        new ConcurrentHashMap<>();
    /** Only populated while a world's vanilla entity autosave pass is in flight. */
    private final ConcurrentHashMap<WorldContextImpl, EntitySaveWave> entitySaveWaves =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Entity, TickingRegistration> tickingEntities =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkContextImpl,
        ConcurrentHashMap<Entity, TickingRegistration>> tickingByContext =
        new ConcurrentHashMap<>();
    private final AtomicLong nextEpoch = new AtomicLong(1L);
    private final AtomicLong nextLease = new AtomicLong(1L);
    private final AtomicInteger workerIds = new AtomicInteger();
    private final ContextWorkerLocal<EntityBuffers> entityBuffers =
        ContextWorkerLocal.withInitial(EntityBuffers::new);
    private final ContextWorkerLocal<BlockEntityBuffers> blockEntityBuffers =
        ContextWorkerLocal.withInitial(BlockEntityBuffers::new);
    private final ContextWorkerLocal<OwnedTaskBuffers> ownedTaskBuffers =
        ContextWorkerLocal.withInitial(OwnedTaskBuffers::new);
    private final ContextWorkerLocal<List<Runnable>> dispatchBatch = ContextWorkerLocal.create();
    private final int workerCount;
    private final ForkJoinPool workers;
    private volatile boolean closed;

    public ContextServiceImpl() {
        this(Integer.getInteger("aerogel.context.workers", DEFAULT_WORKERS));
    }

    ContextServiceImpl(int workerCount) {
        if (workerCount < 1) throw new IllegalArgumentException("workerCount must be positive");
        this.workerCount = workerCount;
        ForkJoinPool.ForkJoinWorkerThreadFactory factory = pool -> {
            ContextWorkerThread worker = new ContextWorkerThread(pool);
            worker.setName("Aerogel-Context-" + workerIds.incrementAndGet());
            worker.setDaemon(true);
            worker.setUncaughtExceptionHandler((thread, error) ->
                LOGGER.log(Level.SEVERE, "Uncaught context worker failure in " + thread.getName(), error));
            return worker;
        };
        workers = new ForkJoinPool(workerCount, factory, (thread, error) ->
            LOGGER.log(Level.SEVERE, "Uncaught context scheduler failure", error), true);
    }

    @Override
    public WorldContext world(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        ensureOpen();
        return worldImpl(world);
    }

    WorldContextImpl worldImpl(ServerLevel world) {
        return worlds.computeIfAbsent(world, key -> new WorldContextImpl(this, key));
    }

    @Override
    public Optional<ChunkContext> currentChunk() {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        return scope == null ? Optional.empty() : Optional.of(scope.primary());
    }

    @Override
    public boolean inContext() {
        return ContextThreadState.current() != null;
    }

    @Override
    public void assertContextThread() {
        if (!inContext()) {
            throw new IllegalStateException("Mutable chunk access requires a ChunkContext");
        }
    }

    @Override
    public int workerCount() {
        return workerCount;
    }

    /**
     * CPU headroom that player-driven chunk loading may consume without
     * competing with Context work already running in this pool.
     */
    public int availableWorkerCount() {
        return Math.max(1, workers.getParallelism() - workers.getActiveThreadCount());
    }

    /**
     * Runs one task for each stable ownership stripe on the Context pool and
     * returns only after every owner has published its result. No stripe is ever
     * entered concurrently by two tasks from the same invocation.
     */
    public void invokeOwnedStripes(int taskCount, IntConsumer task) {
        Objects.requireNonNull(task, "task");
        if (taskCount < 1) return;
        if (taskCount == 1 || closed) {
            for (int index = 0; index < taskCount; index++) task.accept(index);
            return;
        }
        workers.invoke(new IndexedDispatchBatch(task, 0, taskCount));
    }

    long nextEpoch() {
        return nextEpoch.getAndIncrement();
    }

    NeighborhoodLease newLease(ChunkContextImpl primary) {
        return new NeighborhoodLease(nextLease.getAndIncrement(), primary);
    }

    boolean dispatch(Runnable task) {
        if (closed) return false;
        List<Runnable> batch = dispatchBatch.get();
        if (batch == null) workers.execute(task);
        else batch.add(task);
        return true;
    }

    /** Creates one reusable, unmounted-while-waiting continuation for an owner lane. */
    Thread newOwnerContinuation(String name, Runnable task) {
        if (closed) return null;
        return Thread.ofVirtual().name(name).unstarted(task);
    }

    /** Accepts an already-published owner commit while shutdown drains the pool. */
    boolean dispatchWorldCommit(Runnable task) {
        if (workers.isShutdown()) return false;
        try {
            workers.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            return false;
        }
    }

    /** Submits non-Context producer work to the same work-stealing pool without joining it. */
    public boolean executeComputation(Runnable task) {
        Objects.requireNonNull(task, "task");
        return dispatch(task);
    }

    /**
     * Preserves vanilla's world-owned all-holders status barrier off the server
     * thread. Holder lifecycle publication must not enter a ChunkContext lane:
     * a native tick may be waiting for the exact holder publication that makes
     * its requested chunk available, so assigning both sides to that lane would
     * form an owner cycle. The per-world commit lane is the single owner of this
     * DistanceManager generation, while unrelated dimensions remain independent.
     */
    public CompletableFuture<Void> runChunkHolderPhases(
        ServerLevel level,
        List<ChunkHolder> holders,
        Consumer<ChunkHolder> statusPhase,
        Function<ChunkHolder, CompletableFuture<Void>> futurePhase
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(holders, "holders");
        Objects.requireNonNull(statusPhase, "statusPhase");
        Objects.requireNonNull(futurePhase, "futurePhase");
        if (holders.isEmpty()) return CompletableFuture.completedFuture(null);
        WorldContextImpl world = worldImpl(level);
        return world.commitLane().submit(() -> holders.forEach(statusPhase))
            .thenCompose(ignored -> {
                CompletableFuture<Void> settled = new CompletableFuture<>();
                world.commitLane().submit(() -> {
                    CompletableFuture<?>[] futures =
                        new CompletableFuture<?>[holders.size()];
                    for (int index = 0; index < holders.size(); index++) {
                        ChunkHolder holder = holders.get(index);
                        futures[index] = Objects.requireNonNull(
                            futurePhase.apply(holder), "futurePhase result");
                    }
                    CompletableFuture.allOf(futures).whenComplete((unused, error) -> {
                        if (error == null) settled.complete(null);
                        else settled.completeExceptionally(error);
                    });
                }).whenComplete((unused, error) -> {
                    if (error != null) settled.completeExceptionally(error);
                });
                return settled;
            });
    }

    private void dispatchBatched(Runnable producer) {
        if (dispatchBatch.get() != null) {
            producer.run();
            return;
        }
        List<Runnable> batch = new ArrayList<>();
        dispatchBatch.set(batch);
        try {
            producer.run();
        } finally {
            dispatchBatch.remove();
            if (!closed && !batch.isEmpty()) {
                workers.execute(new DispatchBatch(batch.toArray(Runnable[]::new), 0, batch.size()));
            }
        }
    }

    public void worldLoaded(ServerLevel level) {
        if (!closed) worldImpl(level);
    }

    public void worldUnloaded(ServerLevel level) {
        WorldContextImpl context = worlds.remove(level);
        if (context != null) {
            EntitySaveWave wave = entitySaveWaves.remove(context);
            if (wave != null) wave.stopAccepting();
            context.close();
        }
    }

    public void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        if (closed) return;
        WorldContextImpl world = worldImpl(level);
        world.attach(chunk);
    }

    public void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        WorldContextImpl world = worlds.get(level);
        if (world != null) world.detach(chunk);
    }

    public boolean drainBeforeChunkUnload(
        ServerLevel level, LevelChunk chunk, Runnable unload
    ) {
        WorldContextImpl world = worlds.get(level);
        return world != null && world.drainBeforeUnload(chunk, unload);
    }

    public void tickEntities(
        ServerLevel level, List<Entity> entities, Consumer<Entity> action
    ) {
        if (entities.isEmpty()) return;
        worldImpl(level);
        EntityBuffers buffers = entityBuffers.get();
        buffers.prepare();
        for (Entity entity : entities) {
            if (entity == null) continue;
            ChunkContextImpl context = resolveOwner(entity);
            if (context == null) {
                buffers.coordinatorEntities.add(entity);
                continue;
            }
            EntityBatch batch = buffers.byContext.get(context);
            if (batch == null) {
                batch = buffers.acquire(context);
                buffers.byContext.put(context, batch);
                buffers.batches.add(batch);
            }
            batch.entities.add(entity);
        }

        dispatchBatched(() -> {
            for (EntityBatch batch : buffers.batches) {
                batch.context.entityLane().offer(batch.entities, action);
            }
        });
        for (Entity entity : buffers.coordinatorEntities) action.accept(entity);
    }

    /**
     * Publishes the exact vanilla ticking-set membership into its owning Context.
     * Membership is changed only from ServerLevel.EntityCallbacks, at the same point
     * where vanilla changes EntityTickList, so this index does not invent a spatial
     * range or change which entities are eligible to tick.
     */
    public void registerTickingEntity(ServerLevel level, Entity entity) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entity, "entity");
        if (closed) return;
        ChunkContextImpl owner = resolveOwner(entity);
        if (owner == null) return;
        TickingRegistration registration = new TickingRegistration(entity, owner);
        TickingRegistration previous = tickingEntities.put(entity, registration);
        if (previous != null) removeTickingRegistration(previous);
        tickingByContext.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>())
            .put(entity, registration);
    }

    public void unregisterTickingEntity(Entity entity) {
        if (entity == null) return;
        TickingRegistration registration = tickingEntities.remove(entity);
        if (registration != null) removeTickingRegistration(registration);
    }

    private void removeTickingRegistration(TickingRegistration registration) {
        ConcurrentHashMap<Entity, TickingRegistration> entries =
            tickingByContext.get(registration.owner);
        if (entries == null) return;
        entries.remove(registration.entity, registration);
        if (entries.isEmpty()) tickingByContext.remove(registration.owner, entries);
    }

    /**
     * Starts one preparation task per non-empty owner Context. Entity enumeration,
     * owner migration, and swept collision-scope construction consequently happen on
     * Context workers rather than in the server-thread tick. Every registration uses
     * a monotonically claimed server tick, so a boundary transfer is neither duplicated
     * nor skipped.
     */
    public void tickRegisteredEntities(
        ServerLevel level, Consumer<Entity> action
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(action, "action");
        if (closed || tickingByContext.isEmpty()) return;
        WorldContextImpl world = worldImpl(level);
        NativeTickToken token = NativeTickCoordinator.currentTickToken();
        if (token == null) return;
        CompletableFuture<Void> producerPass = world.entityTickProducer().offer(token, () -> {
            ArrayList<Map.Entry<ChunkContextImpl,
                ConcurrentHashMap<Entity, TickingRegistration>>> entries =
                new ArrayList<>(tickingByContext.entrySet());
            invokeOwnedStripes(entries.size(), index -> {
                var entry = entries.get(index);
                ChunkContextImpl context = entry.getKey();
                if (context.world() != world) return;
                ConcurrentHashMap<Entity, TickingRegistration> registrations =
                    entry.getValue();
                if (!context.active() || registrations.isEmpty()) return;
                submitTickingContext(context, registrations, action, token);
            });
        });
        world.publishEntityProducerPass(token, producerPass);
    }

    /**
     * Appends each entity-chunk serialization to its exact owner Context. This is
     * an ordering fence, not a lock: a busy owner saves after its already queued
     * mutations, while independent owners serialize concurrently.
     */
    public boolean saveEntityChunks(
        ServerLevel level, LongSet chunks, LongConsumer saveChunk
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(saveChunk, "saveChunk");
        if (closed || chunks.isEmpty()) return false;
        WorldContextImpl world = worlds.get(level);
        if (world == null) return false;

        long[] keys = chunks.toLongArray();
        if (keys.length == 0) return true;
        EntitySaveWave wave = new EntitySaveWave(world, saveChunk, keys.length);
        EntitySaveWave previous = entitySaveWaves.put(world, wave);
        if (previous != null) previous.stopAccepting();
        dispatch(() -> dispatchBatched(() -> {
            for (long key : keys) queueEntitySave(wave, new long[] { key }, null);
        }));
        return true;
    }

    /**
     * Repairs the exact two entity chunks touched by a move that overlaps an
     * autosave wave. The pair is coalesced only while queued and acquires those
     * two Contexts together, so it cannot serialize half of the movement.
     */
    void entityMovedAcrossChunks(
        WorldContextImpl world, long previousChunk, long currentChunk
    ) {
        if (previousChunk == currentChunk) return;
        EntitySaveWave wave = entitySaveWaves.get(world);
        if (wave != null) wave.moved(previousChunk, currentChunk);
    }

    private void queueEntitySave(
        EntitySaveWave wave, long[] scopeKeys, EntityChunkPair pair
    ) {
        Runnable save = () -> {
            if (pair != null) wave.queuedPairs.remove(pair);
            for (long key : scopeKeys) wave.saveChunk.accept(key);
        };
        ChunkContextImpl context = null;
        for (long key : scopeKeys) {
            context = wave.world.existingContext(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (context != null) break;
        }
        if (context == null) {
            NativeTickCoordinator.submitMainThread(() -> {
                try {
                    save.run();
                } finally {
                    wave.complete();
                }
            });
            return;
        }

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                try {
                    save.run();
                } finally {
                    wave.complete();
                }
            });
        };
        boolean accepted = context.submitNative(scopeKeys, () ->
            NativeTickCoordinator.runNative(
                List.of(Boolean.TRUE), ignored -> save.run(), wave::complete), rejected);
        if (!accepted) rejected.run();
    }

    private void submitTickingContext(
        ChunkContextImpl context,
        ConcurrentHashMap<Entity, TickingRegistration> registrations,
        Consumer<Entity> action,
        NativeTickToken token
    ) {
        context.offerTickTask(token, tickState -> {
            NativeTickCoordinator.taskSubmitted();
            Runnable rejected = () -> {
                NativeTickCoordinator.taskRejected();
                context.completeTickTask(tickState);
            };
            boolean accepted = context.submitNativePhase(NativePhase.ENTITY,
                () -> NativeTickCoordinator.runNative(
                List.of(registrations), ignored -> prepareTickingContext(
                    context, registrations, action, token),
                () -> context.completeTickTask(tickState)), rejected);
            if (!accepted) rejected.run();
        }, () -> { });
    }

    private void prepareTickingContext(
        ChunkContextImpl expected,
        ConcurrentHashMap<Entity, TickingRegistration> registrations,
        Consumer<Entity> action,
        NativeTickToken token
    ) {
        long serverTick = token.serverTick();
        List<TickingRegistration> snapshot = List.copyOf(registrations.values());
        List<Entity> local = new ArrayList<>(snapshot.size());
        java.util.IdentityHashMap<Entity, TickingRegistration> claims =
            new java.util.IdentityHashMap<>(snapshot.size());
        for (TickingRegistration registration : snapshot) {
            if (tickingEntities.get(registration.entity) != registration
                || registration.entity.isRemoved()) continue;
            ChunkContextImpl current = resolveOwner(registration.entity);
            if (current != expected) {
                transferTickingRegistration(registration, expected, current);
                if (current != null && registration.claim(serverTick)) {
                    current.entityLane().offer(List.of(registration.entity), entity ->
                        runClaimedEntityTick(registration, entity, action), token);
                }
                continue;
            }
            if (registration.claim(serverTick)) {
                local.add(registration.entity);
                claims.put(registration.entity, registration);
            }
        }
        if (local.isEmpty()) return;
        expected.entityLane().offer(local, entity -> {
            TickingRegistration registration = claims.get(entity);
            if (registration != null) runClaimedEntityTick(registration, entity, action);
        }, token);
    }

    /**
     * A queued logical tick is valid only while the exact vanilla ticking-membership
     * generation that claimed it is still published. An earlier entity tick can move
     * the entity into a non-ticking section before this queued continuation executes;
     * vanilla would never enumerate the entity for the later tick in that state.
     */
    private void runClaimedEntityTick(
        TickingRegistration registration,
        Entity entity,
        Consumer<Entity> action
    ) {
        if (!entity.isRemoved() && tickingEntities.get(entity) == registration) {
            action.accept(entity);
        }
    }

    private void transferTickingRegistration(
        TickingRegistration registration,
        ChunkContextImpl expected,
        ChunkContextImpl current
    ) {
        if (current == null || registration.owner != expected) return;
        ConcurrentHashMap<Entity, TickingRegistration> previous =
            tickingByContext.get(expected);
        if (previous != null) {
            previous.remove(registration.entity, registration);
            if (previous.isEmpty()) tickingByContext.remove(expected, previous);
        }
        registration.owner = current;
        tickingByContext.computeIfAbsent(current, ignored -> new ConcurrentHashMap<>())
            .put(registration.entity, registration);
    }

    /**
     * Routes owner-local follow-up work without rebuilding entity tick scopes.
     *
     * <p>The entity callback publishes its owner when section movement commits. That
     * publication is the authoritative grouping key here. A stale or absent publication
     * uses the normal spatial resolver, while execution rechecks the publication before
     * touching the entity. Unlike a native entity tick, this work neither integrates
     * movement nor reads collision state, so its exact mutable scope is the owner Context.
     */
    public void routeOwnedEntityBatch(
        ServerLevel level, List<Entity> entities, Consumer<Entity> action
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(action, "action");
        if (entities.isEmpty()) return;
        if (closed || !NativeTickCoordinator.acceptsContextRouting()) {
            for (Entity entity : entities) if (entity != null) action.accept(entity);
            return;
        }

        WorldContextImpl world = worldImpl(level);
        EntityBuffers buffers = entityBuffers.get();
        buffers.prepare();
        for (Entity entity : entities) {
            if (entity == null) continue;
            EntityContextOwnerBridge ownership = (EntityContextOwnerBridge) entity;
            Object observed = ownership.aerogel$contextOwner();
            ChunkContextImpl context = observed instanceof ChunkContextImpl owned
                && owned.world() == world && owned.active()
                ? owned
                : resolveOwner(entity);
            if (context == null) {
                buffers.coordinatorEntities.add(entity);
                continue;
            }
            EntityBatch batch = buffers.byContext.get(context);
            if (batch == null) {
                batch = buffers.acquire(context);
                buffers.byContext.put(context, batch);
                buffers.batches.add(batch);
            }
            batch.entities.add(entity);
        }

        dispatchBatched(() -> {
            for (EntityBatch batch : buffers.batches) {
                submitOwnedEntityBatch(level, batch.context, batch.entities, action);
            }
        });
        for (Entity entity : buffers.coordinatorEntities) action.accept(entity);
    }

    private void submitOwnedEntityBatch(
        ServerLevel level,
        ChunkContextImpl context,
        List<Entity> entities,
        Consumer<Entity> action
    ) {
        List<Entity> immutableBatch = List.copyOf(entities);
        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() ->
                routeOwnedEntityBatch(level, immutableBatch, action));
        };
        boolean accepted = context.submitNative(() -> NativeTickCoordinator.runNative(
            immutableBatch,
            entity -> runPublishedOwnerAction(context, entity, action),
            () -> { }), rejected);
        if (!accepted) rejected.run();
    }

    private void runPublishedOwnerAction(
        ChunkContextImpl expected, Entity entity, Consumer<Entity> action
    ) {
        Object observed = ((EntityContextOwnerBridge) entity).aerogel$contextOwner();
        if (observed == expected) {
            action.accept(entity);
            return;
        }
        runRouted(expected, entity, () -> action.accept(entity));
    }

    public void routeOwnedEntityTasks(
        ServerLevel level, List<? extends ContextOwnedEntityTask> tasks
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(tasks, "tasks");
        if (tasks.isEmpty()) return;
        if (closed || !NativeTickCoordinator.acceptsContextRouting()) {
            for (ContextOwnedEntityTask task : tasks) task.aerogel$run();
            return;
        }

        WorldContextImpl world = worldImpl(level);
        OwnedTaskBuffers buffers = ownedTaskBuffers.get();
        buffers.prepare();
        for (ContextOwnedEntityTask task : tasks) {
            if (task == null || task.aerogel$entity() == null) continue;
            Entity entity = task.aerogel$entity();
            Object observed = ((EntityContextOwnerBridge) entity).aerogel$contextOwner();
            ChunkContextImpl context = observed instanceof ChunkContextImpl owned
                && owned.world() == world && owned.active()
                ? owned
                : resolveOwner(entity);
            if (context == null) {
                buffers.coordinatorTasks.add(task);
                continue;
            }
            OwnedTaskBatch batch = buffers.byContext.get(context);
            if (batch == null) {
                batch = buffers.acquire(context);
                buffers.byContext.put(context, batch);
                buffers.batches.add(batch);
            }
            batch.tasks.add(task);
        }

        dispatchBatched(() -> {
            for (OwnedTaskBatch batch : buffers.batches) {
                submitOwnedEntityTasks(level, batch.context, batch.tasks);
            }
        });
        for (ContextOwnedEntityTask task : buffers.coordinatorTasks) task.aerogel$run();
    }

    private void submitOwnedEntityTasks(
        ServerLevel level,
        ChunkContextImpl context,
        List<ContextOwnedEntityTask> tasks
    ) {
        List<ContextOwnedEntityTask> immutableBatch = List.copyOf(tasks);
        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() ->
                routeOwnedEntityTasks(level, immutableBatch));
        };
        boolean accepted = context.submitNative(() -> NativeTickCoordinator.runNative(
            immutableBatch,
            task -> runPublishedOwnerAction(
                context, task.aerogel$entity(), ignored -> task.aerogel$run()),
            () -> { }), rejected);
        if (!accepted) rejected.run();
    }

    public void tickChunks(
        ServerLevel level, ChunkMap chunkMap, Consumer<LevelChunk> action
    ) {
        WorldContextImpl world = worldImpl(level);
        NativeTickToken token = NativeTickCoordinator.currentTickToken();
        if (token == null) return;
        world.chunkTickProducer().offer(token, () -> {
            ArrayList<LevelChunk> chunks = new ArrayList<>();
            chunkMap.forEachBlockTickingChunk(chunks::add);
            invokeOwnedStripes(chunks.size(), index -> {
                LevelChunk chunk = chunks.get(index);
                world.context(chunk).chunkLane().offer(chunk, action, token);
            });
        });
    }

    public void registerTrackedEntity(
        ServerLevel level, Entity entity, TrackedEntityBridge tracked
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(tracked, "tracked");
        ChunkContextImpl owner = resolveOwner(entity);
        if (owner == null) return;
        TrackedRegistration registration = new TrackedRegistration(entity, tracked, owner);
        TrackedRegistration previous = trackedEntities.put(entity, registration);
        if (previous != null) removeTrackedRegistration(previous);
        trackedByContext.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>())
            .put(entity, registration);
    }

    public void unregisterTrackedEntity(Entity entity) {
        if (entity == null) return;
        TrackedRegistration registration = trackedEntities.remove(entity);
        if (registration != null) removeTrackedRegistration(registration);
    }

    private void removeTrackedRegistration(TrackedRegistration registration) {
        ConcurrentHashMap<Entity, TrackedRegistration> entries =
            trackedByContext.get(registration.owner);
        if (entries == null) return;
        entries.remove(registration.entity, registration);
        if (entries.isEmpty()) trackedByContext.remove(registration.owner, entries);
    }

    public void tickTrackedEntities(
        ServerLevel level,
        List<ServerPlayer> players
    ) {
        if (closed || trackedByContext.isEmpty()) return;
        List<ServerPlayer> playerSnapshot = List.copyOf(players);
        Set<ServerPlayer> knownViewers = trackingViewers.computeIfAbsent(
            level, ignored -> ConcurrentHashMap.newKeySet());
        knownViewers.retainAll(playerSnapshot);
        List<ServerPlayer> movedPlayers = new ArrayList<>();
        for (ServerPlayer player : playerSnapshot) {
            boolean newViewer = knownViewers.add(player);
            TrackedRegistration registration = trackedEntities.get(player);
            if (newViewer || registration != null
                && registration.tracked.aerogel$sectionChanged()) {
                movedPlayers.add(player);
            }
        }
        List<ServerPlayer> movedSnapshot = List.copyOf(movedPlayers);
        NativeTickToken token = NativeTickCoordinator.currentTickToken();
        if (token == null) return;
        WorldContextImpl world = worldImpl(level);
        Runnable produceTracking = () -> {
            ArrayList<Map.Entry<ChunkContextImpl,
                ConcurrentHashMap<Entity, TrackedRegistration>>> entries =
                new ArrayList<>(trackedByContext.entrySet());
            invokeOwnedStripes(entries.size(), index -> {
                var entry = entries.get(index);
                ChunkContextImpl context = entry.getKey();
                if (context.world().level() != level) return;
                ConcurrentHashMap<Entity, TrackedRegistration> registrations =
                    entry.getValue();
                if (!context.active() || registrations.isEmpty()) return;
                submitTrackingContext(context, registrations, playerSnapshot,
                    movedSnapshot, token);
            });
        };
        CompletableFuture<Void> entityPass = world.entityProducerPass(token);
        if (entityPass == null || !token.retainProducer()) {
            world.trackingTickProducer().offer(token, produceTracking);
            return;
        }
        entityPass.whenComplete((ignored, failure) -> {
            try {
                world.trackingTickProducer().offer(token, produceTracking);
            } finally {
                token.releaseProducer();
            }
        });
    }

    private void submitTrackingContext(
        ChunkContextImpl context,
        ConcurrentHashMap<Entity, TrackedRegistration> registrations,
        List<ServerPlayer> players,
        List<ServerPlayer> movedPlayers,
        NativeTickToken token
    ) {
        context.offerTickTask(token, tickState -> {
            NativeTickCoordinator.taskSubmitted();
            Runnable rejected = () -> {
                NativeTickCoordinator.taskRejected();
                context.completeTickTask(tickState);
            };
            boolean accepted = context.submitNativePhase(NativePhase.TRACKING, () -> {
                List<TrackedRegistration> snapshot = List.copyOf(registrations.values());
                NativeTickCoordinator.runNative(snapshot, registration ->
                    runTrackingRegistration(context, registration, players,
                        movedPlayers, token),
                    () -> context.completeTickTask(tickState));
            }, rejected);
            if (!accepted) rejected.run();
        }, () -> { });
    }

    private void runTrackingRegistration(
        ChunkContextImpl expected,
        TrackedRegistration registration,
        List<ServerPlayer> players,
        List<ServerPlayer> movedPlayers,
        NativeTickToken token
    ) {
        long serverTick = token.serverTick();
        if (trackedEntities.get(registration.entity) != registration
            || registration.entity.isRemoved()) return;
        ChunkContextImpl current = resolveOwner(registration.entity);
        if (current != expected) {
            transferTrackedRegistration(registration, expected, current);
            if (current != null) {
                ConcurrentHashMap<Entity, TrackedRegistration> registrations =
                    trackedByContext.get(current);
                if (registrations != null) submitTrackingContext(current, registrations,
                    players, movedPlayers, token);
            }
            return;
        }
        if (!registration.claim(serverTick)) return;
        registration.publishing = true;
        try {
            registration.tracked.aerogel$tickTracking(
                players, tickingEntities.containsKey(registration.entity));
            if (!movedPlayers.isEmpty()) {
                registration.tracked.aerogel$updatePlayers(movedPlayers);
            }
        } finally {
            registration.publishing = false;
        }
    }

    /**
     * Publishes entity state changed by an interactive owner transaction before
     * returning to tick backlog. Normal entity ticks keep vanilla's publication
     * cadence; packet names and entity types do not participate in this decision.
     */
    public void entityTrackingDirty(Entity entity) {
        if (closed || entity == null) return;
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || !scope.interactive()) return;
        TrackedRegistration registration = trackedEntities.get(entity);
        if (registration == null || registration.publishing
            || !registration.dirtyPublicationPending.compareAndSet(false, true)) return;

        Runnable publication = () -> publishDirtyRegistration(registration);
        if (!NativeTickCoordinator.deferNativeCompletion(publication)) {
            registration.dirtyPublicationPending.set(false);
        }
    }

    private void publishDirtyRegistration(TrackedRegistration registration) {
        if (trackedEntities.get(registration.entity) != registration
            || registration.entity.isRemoved()) {
            registration.dirtyPublicationPending.set(false);
            return;
        }
        ChunkContextImpl owner = resolveOwner(registration.entity);
        if (owner == null) {
            registration.dirtyPublicationPending.set(false);
            return;
        }
        if (owner.current()) {
            publishDirtyOwned(registration, owner);
            return;
        }

        NativeTickCoordinator.taskSubmitted();
        AtomicBoolean rejectedOnce = new AtomicBoolean();
        Runnable rejected = () -> {
            if (rejectedOnce.compareAndSet(false, true)) {
                NativeTickCoordinator.taskRejected();
            }
            registration.dirtyPublicationPending.set(false);
        };
        boolean accepted = owner.submitInteractiveNative(() ->
            NativeTickCoordinator.runNative(List.of(registration.entity), ignored ->
                publishDirtyOwned(registration, owner), () -> { }), rejected);
        if (!accepted) rejected.run();
    }

    private void publishDirtyOwned(
        TrackedRegistration registration, ChunkContextImpl expected
    ) {
        if (trackedEntities.get(registration.entity) != registration
            || registration.entity.isRemoved()) {
            registration.dirtyPublicationPending.set(false);
            return;
        }
        ChunkContextImpl current = resolveOwner(registration.entity);
        if (current != expected) {
            registration.dirtyPublicationPending.set(false);
            entityTrackingDirty(registration.entity);
            return;
        }
        registration.publishing = true;
        try {
            registration.tracked.aerogel$publishDirtyState();
        } finally {
            registration.publishing = false;
            registration.dirtyPublicationPending.set(false);
        }
    }

    private void transferTrackedRegistration(
        TrackedRegistration registration,
        ChunkContextImpl expected,
        ChunkContextImpl current
    ) {
        if (current == null || registration.owner != expected) return;
        ConcurrentHashMap<Entity, TrackedRegistration> previous =
            trackedByContext.get(expected);
        if (previous != null) {
            previous.remove(registration.entity, registration);
            if (previous.isEmpty()) trackedByContext.remove(expected, previous);
        }
        registration.owner = current;
        trackedByContext.computeIfAbsent(current, ignored -> new ConcurrentHashMap<>())
            .put(registration.entity, registration);
    }

    /**
     * Publishes the entities owned by a chunk only after that chunk's packet has
     * entered the viewer's connection. This preserves vanilla's chunk-before-entity
     * packet order while keeping the work local to the owning Context.
     */
    public void playerChunkSent(
        ServerLevel level, LevelChunk chunk, ServerPlayer viewer
    ) {
        if (closed || viewer.isRemoved() || viewer.level() != level) return;
        WorldContextImpl world = worlds.get(level);
        if (world == null) return;
        ChunkPos position = chunk.getPos();
        ChunkContextImpl context = world.existingContext(position.x(), position.z());
        if (context == null) return;
        Runnable publish = () -> {
            ConcurrentHashMap<Entity, TrackedRegistration> registrations =
                trackedByContext.get(context);
            if (registrations == null || registrations.isEmpty()) return;
            for (TrackedRegistration registration : registrations.values()) {
                if (trackedEntities.get(registration.entity) == registration
                    && !registration.entity.isRemoved()
                    && registration.owner == context) {
                    registration.tracked.aerogel$updatePlayer(viewer);
                }
            }
        };
        if (context.current()) {
            publish.run();
        } else {
            context.submitNative(publish, () -> { });
        }
    }

    public void tickSpawningChunk(
        ServerLevel level, LevelChunk chunk,
        NaturalSpawner.SpawnState state, Runnable action
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(action, "action");
        NaturalSpawnWave wave = state instanceof PreparedSpawnStateBridge prepared
            ? prepared.aerogel$spawnWave() : null;
        if (wave != null && !wave.register()) {
            throw new IllegalStateException("Natural-spawn wave closed before chunk work");
        }
        Runnable complete = wave == null ? () -> { } : wave::taskComplete;
        worldImpl(level).context(chunk).chunkLane().offer(
            chunk, ignored -> action.run(), complete, null);
    }

    /**
     * Builds vanilla's exact global spawn state away from the server thread. Only
     * natural-spawn work depends on the result; packet handling and unrelated Contexts
     * continue independently while the entity iterable is counted.
     */
    public NaturalSpawner.SpawnState prepareNaturalSpawnState(
        ServerLevel level,
        int spawnableChunks,
        Iterable<Entity> entities,
        NaturalSpawner.ChunkGetter chunkGetter,
        LocalMobCapCalculator localCaps
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(chunkGetter, "chunkGetter");
        Objects.requireNonNull(localCaps, "localCaps");
        ((LocalMobCapSnapshotBridge) localCaps).aerogel$snapshotPlayers(
            List.copyOf(level.players()));
        NaturalSpawner.SpawnState placeholder = NaturalSpawner.createState(
            spawnableChunks, List.of(), chunkGetter, localCaps);
        CompletableFuture<NaturalSpawner.SpawnState> prepared = new CompletableFuture<>();
        NaturalSpawnWave wave = worldImpl(level).beginNaturalSpawnWave();
        ((PreparedSpawnStateBridge) placeholder).aerogel$preparedState(prepared, wave);
        wave.whenActive(() -> dispatch(() -> {
            try {
                // ConcurrentInt2ObjectMap exposes a stable snapshot from values().
                // Acquire it only after the preceding wave (including its global
                // entity-index publications) has completed; retaining the iterable
                // supplied by the server tick would count an arbitrarily stale world.
                prepared.complete(NaturalSpawner.createState(
                    spawnableChunks, level.getAllEntities(), chunkGetter, localCaps));
            } catch (Throwable error) {
                prepared.completeExceptionally(error);
                LOGGER.log(Level.SEVERE, "Could not prepare natural-spawn state", error);
            } finally {
                wave.preparationComplete();
            }
        }), () -> prepared.cancel(false));
        return placeholder;
    }

    public void sealNaturalSpawnWave(NaturalSpawner.SpawnState state) {
        if (state instanceof PreparedSpawnStateBridge prepared) {
            NaturalSpawnWave wave = prepared.aerogel$spawnWave();
            if (wave != null) wave.seal();
        }
    }

    public void withPreparedNaturalSpawnState(
        NaturalSpawner.SpawnState state,
        List<net.minecraft.world.entity.MobCategory> gatedCategories,
        java.util.function.BiConsumer<NaturalSpawner.SpawnState,
            List<net.minecraft.world.entity.MobCategory>> action
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(gatedCategories, "gatedCategories");
        Objects.requireNonNull(action, "action");
        if (state instanceof PreparedSpawnStateBridge prepared) {
            prepared.aerogel$whenPrepared(gatedCategories, action);
        } else {
            action.accept(state, gatedCategories);
        }
    }

    public void tickBlockEntities(
        ServerLevel level,
        List<TickingBlockEntity> tickers,
        boolean runsNormally
    ) {
        WorldContextImpl world = worldImpl(level);
        NativeTickToken token = NativeTickCoordinator.currentTickToken();
        if (token == null) return;
        world.blockEntityTickProducer().offer(token, () -> {
            BlockEntityBuffers buffers = blockEntityBuffers.get();
            buffers.prepare();
            for (TickingBlockEntity ticker : tickers) {
                if (ticker == null) continue;
                if (ticker.isRemoved()) {
                    tickers.remove(ticker);
                    continue;
                }
                BlockPos position = ticker.getPos();
                ChunkContextImpl context = world.existingContext(
                    position.getX() >> 4, position.getZ() >> 4);
                if (context == null) continue;
                long key = WorldContextImpl.key(
                    position.getX() >> 4, position.getZ() >> 4);
                BlockEntityBatch batch = buffers.byChunk.get(key);
                if (batch == null) {
                    batch = buffers.acquire(context);
                    buffers.byChunk.put(key, batch);
                    buffers.batches.add(batch);
                }
                batch.blockEntities.add(ticker);
            }

            dispatchBatched(() -> {
                for (BlockEntityBatch batch : buffers.batches) {
                    batch.context.blockEntityLane().offer(batch.blockEntities, ticker -> {
                        if (ticker.isRemoved()) {
                            tickers.remove(ticker);
                        } else if (runsNormally
                            && level.shouldTickBlocksAt(ticker.getPos())) {
                            ticker.tick();
                        }
                    }, token);
                }
            });
        });
    }

    public boolean routeChunkTask(ServerLevel level, LevelChunk chunk, Runnable action) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(action, "action");
        if (closed || !NativeTickCoordinator.acceptsContextRouting()) return false;
        ChunkContextImpl context = worldImpl(level).context(chunk);
        if (context.current()) return false;

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = NativeTickCoordinator::taskRejected;
        boolean accepted = context.submitSnapshot(() -> NativeTickCoordinator.runNative(
            List.of(action), Runnable::run, () -> { }), rejected);
        if (!accepted) NativeTickCoordinator.taskRejected();
        return accepted;
    }

    @SuppressWarnings("serial")
    private static final class DispatchBatch extends RecursiveAction {
        private final Runnable[] tasks;
        private final int from;
        private final int to;

        private DispatchBatch(Runnable[] tasks, int from, int to) {
            this.tasks = tasks;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int length = to - from;
            if (length == 1) {
                tasks[from].run();
                return;
            }
            int middle = from + length / 2;
            invokeAll(
                new DispatchBatch(tasks, from, middle),
                new DispatchBatch(tasks, middle, to));
        }
    }

    @SuppressWarnings("serial")
    private static final class IndexedDispatchBatch extends RecursiveAction {
        private final IntConsumer task;
        private final int from;
        private final int to;

        private IndexedDispatchBatch(IntConsumer task, int from, int to) {
            this.task = task;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            int length = to - from;
            if (length <= 0) return;
            int stripes = Math.min(length,
                Math.max(1, getPool() == null ? 1 : getPool().getParallelism()));
            if (stripes == 1) {
                for (int index = from; index < to; index++) task.accept(index);
                return;
            }
            IndexedStripe[] workers = new IndexedStripe[stripes];
            for (int stripe = 0; stripe < stripes; stripe++) {
                workers[stripe] = new IndexedStripe(
                    task, from + stripe, to, stripes);
            }
            invokeAll(workers);
        }
    }

    @SuppressWarnings("serial")
    private static final class IndexedStripe extends RecursiveAction {
        private final IntConsumer task;
        private final int first;
        private final int to;
        private final int stride;

        private IndexedStripe(IntConsumer task, int first, int to, int stride) {
            this.task = task;
            this.first = first;
            this.to = to;
            this.stride = stride;
        }

        @Override
        protected void compute() {
            for (int index = first; index < to; index += stride) task.accept(index);
        }
    }

    public boolean routeBlockTask(ServerLevel level, BlockPos position, Runnable action) {
        return routeScopedBlockTask(
            level, position, blockMutationScope(position), action);
    }

    /**
     * Routes a neighbor callback with the exact horizontal chunk footprint that
     * vanilla block-state and redstone evaluation may read from the target block.
     * Interior positions still own one chunk; a face/corner adds only the chunks
     * actually reached by the six axial neighbors.
     */
    public boolean routeNeighborTask(
        ServerLevel level, BlockPos position, Runnable action
    ) {
        return routeScopedBlockTask(
            level, position, neighborMutationScope(position), action, false);
    }

    public boolean routeNeighborTask(
        ServerLevel level, BlockPos position, Runnable action,
        Runnable terminalRejection
    ) {
        Objects.requireNonNull(terminalRejection, "terminalRejection");
        return routeScopedBlockTask(
            level, position, neighborMutationScope(position), action, false,
            terminalRejection);
    }

    public boolean deferNeighborChain(
        ServerLevel level, BlockPos position, Runnable action
    ) {
        return routeScopedBlockTask(
            level, position, neighborMutationScope(position), action, true);
    }

    private boolean routeScopedBlockTask(
        ServerLevel level, BlockPos position, long[] initialScope, Runnable action
    ) {
        return routeScopedBlockTask(level, position, initialScope, action, false);
    }

    private boolean routeScopedBlockTask(
        ServerLevel level, BlockPos position, long[] initialScope, Runnable action,
        boolean forceCausalHandoff
    ) {
        return routeScopedBlockTask(
            level, position, initialScope, action, forceCausalHandoff, null);
    }

    private boolean routeScopedBlockTask(
        ServerLevel level, BlockPos position, long[] initialScope, Runnable action,
        boolean forceCausalHandoff, Runnable terminalRejection
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        if (closed) return false;
        Runnable routedAction = ScheduledTickQueryScope.propagate(action);
        WorldContextImpl world = worlds.get(level);
        ChunkContextImpl context = resolveChunk(level, position.getX() >> 4, position.getZ() >> 4);
        if (world == null || context == null) return false;
        LongOpenHashSet requestedScope = new LongOpenHashSet(initialScope);
        ContextThreadState.AccessScope currentScope = ContextThreadState.current();
        NeighborCausalGroup causalGroup = NeighborCausalExecution.current();
        ChunkContextImpl primary = context;
        if (currentScope != null && currentScope.primary().world() == world) {
            boolean allOwned = true;
            for (long key : requestedScope.toLongArray()) {
                if (!currentScope.containsKey(key)) {
                    allOwned = false;
                    break;
                }
            }
            if (allOwned && !forceCausalHandoff) return false;
            if (causalGroup == null) {
                // Ordinary one-shot mutations carry their source scope into the
                // hand-off. A neighbor group instead retains causal claims for its
                // full lifetime, so repeatedly copying every previously visited
                // Context would turn a long wire into quadratic acquisition work.
                requestedScope.add(currentScope.primary().key());
                if (currentScope.ownedKeys() != null) {
                    for (long key : currentScope.ownedKeys().toLongArray()) {
                        requestedScope.add(key);
                    }
                }
                primary = currentScope.primary();
            }
        }
        long[] scopeKeys = requestedScope.toLongArray();

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected;
        if (causalGroup != null) {
            rejected = () -> {
                NativeTickCoordinator.taskRejected();
                if (terminalRejection != null) terminalRejection.run();
            };
        } else {
            rejected = () -> {
                NativeTickCoordinator.taskRejected();
                NativeTickCoordinator.submitMainThread(() -> {
                    if (!routeScopedBlockTask(
                        level, position, initialScope, routedAction,
                        forceCausalHandoff)) routedAction.run();
                });
            };
        }
        Runnable nativeAction = () -> NativeTickCoordinator.runNative(
            List.of(routedAction), Runnable::run, () -> { });
        boolean accepted = causalGroup == null
            ? primary.submitNative(scopeKeys, nativeAction, rejected,
                NativeTickCoordinator::taskRejected)
            : primary.submitCausalNative(causalGroup, scopeKeys, nativeAction, rejected,
                rejected);
        if (!accepted) {
            NativeTickCoordinator.taskRejected();
            return false;
        }
        return true;
    }

    /**
     * Owns every loaded chunk whose listener registry vanilla's dispatcher reads.
     * The range comes directly from the posted event's notification radius; it is
     * neither guessed nor widened. Overlapping game events and listener lifecycle
     * changes are consequently ordered by the same Context ownership mechanism as
     * the blocks and entities that produced them.
     */
    public boolean routeGameEvent(
        ServerLevel level, Holder<GameEvent> event, Vec3 position,
        GameEvent.Context eventContext
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(eventContext, "eventContext");
        if (closed) return false;
        WorldContextImpl world = worlds.get(level);
        if (world == null) return false;

        int blockX = (int) Math.floor(position.x);
        int blockZ = (int) Math.floor(position.z);
        long[] candidates = gameEventScope(
            blockX, blockZ, event.value().notificationRadius());
        LongOpenHashSet loadedScope = new LongOpenHashSet();
        ChunkContextImpl primary = null;
        ContextThreadState.AccessScope currentScope = ContextThreadState.current();
        for (long key : candidates) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) continue;
            ChunkContextImpl context = world.context(chunk);
            loadedScope.add(key);
            if (primary == null && context.active()) primary = context;
        }
        if (loadedScope.isEmpty() || primary == null) return false;
        if (currentScope != null && currentScope.primary().world() == world
            && loadedScope.contains(currentScope.primary().key())) {
            primary = currentScope.primary();
        }

        long[] scopeKeys = loadedScope.toLongArray();
        if (currentScope != null && currentScope.primary().world() == world) {
            boolean allOwned = true;
            for (long key : scopeKeys) {
                if (!currentScope.containsKey(key)) {
                    allOwned = false;
                    break;
                }
            }
            if (allOwned) return false;
        }

        Runnable post = () -> level.gameEvent(event, position, eventContext);
        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeGameEvent(level, event, position, eventContext)) post.run();
            });
        };
        boolean accepted = primary.submitNative(scopeKeys,
            () -> NativeTickCoordinator.runNative(
                List.of(post), Runnable::run, () -> { }), rejected,
                NativeTickCoordinator::taskRejected);
        if (!accepted) {
            NativeTickCoordinator.taskRejected();
            return false;
        }
        return true;
    }

    /**
     * Orders a dynamic game-event listener's old and new registries with event
     * delivery to those exact chunks. Vanilla's registry protects re-entrancy on
     * one thread, not concurrent ArrayList mutation, so lifecycle changes must
     * share the same owner set as dispatch.
     */
    public boolean routeGameEventListenerMutation(
        ServerLevel level, long[] candidateKeys, Runnable action
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(candidateKeys, "candidateKeys");
        Objects.requireNonNull(action, "action");
        if (closed || candidateKeys.length == 0) return false;
        WorldContextImpl world = worlds.get(level);
        if (world == null) return false;

        LongOpenHashSet loadedScope = new LongOpenHashSet();
        ChunkContextImpl primary = null;
        ContextThreadState.AccessScope currentScope = ContextThreadState.current();
        for (long key : candidateKeys) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) continue;
            ChunkContextImpl context = world.context(chunk);
            loadedScope.add(key);
            if (primary == null && context.active()) primary = context;
        }
        if (loadedScope.isEmpty() || primary == null) return false;
        if (currentScope != null && currentScope.primary().world() == world
            && loadedScope.contains(currentScope.primary().key())) {
            primary = currentScope.primary();
        }

        long[] scopeKeys = loadedScope.toLongArray();
        if (currentScope != null && currentScope.primary().world() == world) {
            boolean allOwned = true;
            for (long key : scopeKeys) {
                if (!currentScope.containsKey(key)) {
                    allOwned = false;
                    break;
                }
            }
            if (allOwned) return false;
        }

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeGameEventListenerMutation(level, candidateKeys, action)) {
                    action.run();
                }
            });
        };
        boolean accepted = primary.submitNative(scopeKeys,
            () -> NativeTickCoordinator.runNative(
                List.of(action), Runnable::run, () -> { }), rejected,
                NativeTickCoordinator::taskRejected);
        if (!accepted) {
            NativeTickCoordinator.taskRejected();
            return false;
        }
        return true;
    }

    static long[] gameEventScope(int blockX, int blockZ, int notificationRadius) {
        if (notificationRadius < 0) {
            throw new IllegalArgumentException("notification radius must not be negative");
        }
        int minimumChunkX = (blockX - notificationRadius) >> 4;
        int maximumChunkX = (blockX + notificationRadius) >> 4;
        int minimumChunkZ = (blockZ - notificationRadius) >> 4;
        int maximumChunkZ = (blockZ + notificationRadius) >> 4;
        LongOpenHashSet keys = new LongOpenHashSet();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                keys.add(WorldContextImpl.key(chunkX, chunkZ));
            }
        }
        return keys.toLongArray();
    }

    /**
     * Continues vanilla's comparator-output notification on the exact chunks that
     * {@code Level.updateNeighbourForOutputSignal} reads. A visible chunk holder can
     * exist before its FULL future is complete; a context worker must not synchronously
     * join that future, so each dependent read resumes in that position's owner context.
     */
    public boolean routeOutputSignalUpdate(
        ServerLevel level, BlockPos sourcePosition, Block sourceBlock
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(sourceBlock, "sourceBlock");
        if (!NativeTickCoordinator.isNativeWorker() || closed) return false;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos first = sourcePosition.relative(direction).immutable();
            if (!level.hasChunkAt(first)) continue;
            continueWhenFull(level, first, false, () -> {
                BlockState state = level.getBlockState(first);
                if (state.is(Blocks.COMPARATOR)) {
                    level.neighborChanged(state, first, sourceBlock, null, false);
                    return;
                }
                if (!state.isRedstoneConductor(level, first)) return;

                BlockPos second = first.relative(direction).immutable();
                continueWhenFull(level, second, true, () -> {
                    BlockState secondState = level.getBlockState(second);
                    if (secondState.is(Blocks.COMPARATOR)) {
                        level.neighborChanged(
                            secondState, second, sourceBlock, null, false);
                    }
                });
            });
        }
        return true;
    }

    private void continueWhenFull(
        ServerLevel level, BlockPos position, boolean create, Runnable continuation
    ) {
        if (isBlockOwnerContext(level, position)) {
            continuation.run();
            return;
        }
        if (routeBlockTask(level, position, continuation)) return;

        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        CompletableFuture<ChunkResult<ChunkAccess>> future = level.getChunkSource()
            .getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, create);
        future.whenComplete((result, error) -> {
            if (error != null) {
                LOGGER.log(Level.WARNING,
                    "Could not continue output-signal update at " + position, error);
                return;
            }
            if (closed || result.orElse(null) == null) return;
            routeBlockTask(level, position, continuation);
        });
    }

    static long[] blockMutationScope(BlockPos position) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        return new long[] { WorldContextImpl.key(chunkX, chunkZ) };
    }

    static long[] neighborMutationScope(BlockPos position) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        LongOpenHashSet keys = new LongOpenHashSet();
        keys.add(WorldContextImpl.key(chunkX, chunkZ));
        int localX = position.getX() & 15;
        int localZ = position.getZ() & 15;
        if (localX == 0) keys.add(WorldContextImpl.key(chunkX - 1, chunkZ));
        else if (localX == 15) keys.add(WorldContextImpl.key(chunkX + 1, chunkZ));
        if (localZ == 0) keys.add(WorldContextImpl.key(chunkX, chunkZ - 1));
        else if (localZ == 15) keys.add(WorldContextImpl.key(chunkX, chunkZ + 1));
        return keys.toLongArray();
    }

    public boolean routeBlockEffects(
        ServerLevel level, Iterable<BlockPos> positions, Runnable action
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(action, "action");
        if (closed) return false;
        long[] scopeKeys = blockEffectScope(positions);
        if (scopeKeys.length == 0) return false;
        WorldContextImpl world = worldImpl(level);
        ContextThreadState.AccessScope currentScope = ContextThreadState.current();
        if (currentScope != null && currentScope.primary().world() == world) {
            boolean allOwned = true;
            for (long key : scopeKeys) {
                if (!currentScope.containsKey(key)) {
                    allOwned = false;
                    break;
                }
            }
            if (allOwned) return false;
        }
        ChunkContextImpl primary = currentScope != null
            && currentScope.primary().world() == world
            ? currentScope.primary()
            : world.context(ChunkPos.getX(scopeKeys[0]), ChunkPos.getZ(scopeKeys[0]));
        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeBlockEffects(level, positions, action)) action.run();
            });
        };
        boolean accepted = primary.submitNative(scopeKeys,
            () -> NativeTickCoordinator.runNative(
                List.of(action), Runnable::run, () -> { }), rejected,
                NativeTickCoordinator::taskRejected);
        if (!accepted) NativeTickCoordinator.taskRejected();
        return accepted;
    }

    static long[] blockEffectScope(Iterable<BlockPos> positions) {
        LongOpenHashSet keys = new LongOpenHashSet();
        for (BlockPos position : positions) {
            if (position == null) continue;
            for (long key : blockMutationScope(position)) keys.add(key);
        }
        return keys.toLongArray();
    }

    /** Exact chunk faces touched by the entity's current and projected swept box. */
    static long[] entityTickScope(ChunkContextImpl owner, Entity entity) {
        LongOpenHashSet keys = new LongOpenHashSet();
        addEntityTickScope(keys, owner, entity);
        return keys.toLongArray();
    }

    /** Allocation-free fast path for validating a previously admitted entity scope. */
    static boolean entityTickScopeCovered(
        ChunkContextImpl owner, Entity entity, long[] sortedAdmitted
    ) {
        if (Arrays.binarySearch(sortedAdmitted, owner.key()) < 0) return false;
        if (entity instanceof EntityContextScopeBridge additional) {
            BlockPos position = additional.aerogel$additionalContextBlock(
                owner.world().level());
            if (position != null && Arrays.binarySearch(sortedAdmitted,
                WorldContextImpl.key(position.getX() >> 4, position.getZ() >> 4)) < 0) {
                return false;
            }
        }

        AABB box = entity.getBoundingBox();
        if (box == null) return true;
        Vec3 movement = entity.getDeltaMovement();
        double moveX = movement == null || !Double.isFinite(movement.x) ? 0.0D : movement.x;
        double moveZ = movement == null || !Double.isFinite(movement.z) ? 0.0D : movement.z;
        double minX = Math.min(box.minX, box.minX + moveX);
        double minZ = Math.min(box.minZ, box.minZ + moveZ);
        double maxX = Math.max(box.maxX, box.maxX + moveX);
        double maxZ = Math.max(box.maxZ, box.maxZ + moveZ);
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) return true;

        int minChunkX = ((int) Math.floor(minX)) >> 4;
        int minChunkZ = ((int) Math.floor(minZ)) >> 4;
        int maxChunkX = ((int) Math.floor(maxX > minX ? Math.nextDown(maxX) : maxX)) >> 4;
        int maxChunkZ = ((int) Math.floor(maxZ > minZ ? Math.nextDown(maxZ) : maxZ)) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (Arrays.binarySearch(sortedAdmitted,
                    WorldContextImpl.key(chunkX, chunkZ)) < 0) return false;
            }
        }
        return true;
    }

    /** Exact union of all swept entity footprints in one owner-chunk tick batch. */
    static long[] entityTickScope(ChunkContextImpl owner, Iterable<Entity> entities) {
        LongOpenHashSet keys = new LongOpenHashSet();
        keys.add(owner.key());
        for (Entity entity : entities) addEntityTickScope(keys, owner, entity);
        return keys.toLongArray();
    }

    private static void addEntityTickScope(
        LongOpenHashSet keys, ChunkContextImpl owner, Entity entity
    ) {
        keys.add(owner.key());
        if (entity instanceof EntityContextScopeBridge additional) {
            BlockPos position = additional.aerogel$additionalContextBlock(
                owner.world().level());
            if (position != null) addScope(keys, position);
        }
        AABB box = entity.getBoundingBox();
        if (box == null) return;
        Vec3 movement = entity.getDeltaMovement();
        double moveX = movement == null || !Double.isFinite(movement.x) ? 0.0D : movement.x;
        double moveZ = movement == null || !Double.isFinite(movement.z) ? 0.0D : movement.z;
        double minX = Math.min(box.minX, box.minX + moveX);
        double minZ = Math.min(box.minZ, box.minZ + moveZ);
        double maxX = Math.max(box.maxX, box.maxX + moveX);
        double maxZ = Math.max(box.maxZ, box.maxZ + moveZ);
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) return;

        int minBlockX = (int) Math.floor(minX);
        int minBlockZ = (int) Math.floor(minZ);
        int maxBlockX = (int) Math.floor(maxX > minX ? Math.nextDown(maxX) : maxX);
        int maxBlockZ = (int) Math.floor(maxZ > minZ ? Math.nextDown(maxZ) : maxZ);
        int minChunkX = minBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkX = maxBlockX >> 4;
        int maxChunkZ = maxBlockZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                keys.add(WorldContextImpl.key(chunkX, chunkZ));
            }
        }
        addScope(keys, new BlockPos(minBlockX, 0, minBlockZ));
        addScope(keys, new BlockPos(minBlockX, 0, maxBlockZ));
        addScope(keys, new BlockPos(maxBlockX, 0, minBlockZ));
        addScope(keys, new BlockPos(maxBlockX, 0, maxBlockZ));
    }

    private static void addScope(LongOpenHashSet keys, BlockPos position) {
        for (long key : blockMutationScope(position)) keys.add(key);
    }

    public boolean isBlockOwnerContext(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        WorldContextImpl world = worlds.get(level);
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (world == null || scope == null || scope.primary().world() != world) return false;
        for (long key : blockMutationScope(position)) {
            if (!scope.containsKey(key)) return false;
        }
        return true;
    }

    public boolean isNeighborOwnerContext(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        WorldContextImpl world = worlds.get(level);
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (world == null || scope == null || scope.primary().world() != world) return false;
        for (long key : neighborMutationScope(position)) {
            if (!scope.containsKey(key)) return false;
        }
        return true;
    }

    public boolean isChunkOwnerContext(ServerLevel level, LevelChunk chunk) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        WorldContextImpl world = worlds.get(level);
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (world == null || scope == null || scope.primary().world() != world) return false;
        ChunkPos position = chunk.getPos();
        return scope.containsKey(WorldContextImpl.key(position.x(), position.z()));
    }

    /**
     * Routes work originating outside entity ticking through the entity's current owner.
     * Returns false only when the caller must execute the work on its current thread.
     */
    public boolean routeEntityTask(Entity entity, Runnable action) {
        return routeEntityTask(entity, action, false);
    }

    public boolean routeInteractiveEntityTask(Entity entity, Runnable action) {
        return routeEntityTask(entity, action, true);
    }

    private boolean routeEntityTask(
        Entity entity, Runnable action, boolean interactive
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(action, "action");
        if (closed || !NativeTickCoordinator.acceptsContextRouting()) return false;

        ChunkContextImpl context = resolveOwner(entity);
        if (context == null) return false;
        if (ownsCurrentEntityTickScope(context, entity)) return false;
        long[] scopeKeys = entityTickScope(context, entity);

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeEntityTask(entity, action, interactive)) action.run();
            });
        };
        Runnable routed = () -> NativeTickCoordinator.runNative(
            List.of(entity), ignored -> runScopedEntityTask(
                context, entity, action, interactive), () -> { });
        boolean accepted = interactive
            ? context.submitInteractiveNative(scopeKeys, routed, rejected)
            : context.submitNative(scopeKeys, routed, rejected);
        if (!accepted) {
            NativeTickCoordinator.taskRejected();
            return false;
        }
        return true;
    }

    /**
     * Validates dynamic entity dependencies at actual execution admission.
     * Connection ticks and other owner-routed follow-up work can observe state
     * created after queue submission (for example, a newly opened block menu).
     * Requeue the whole action before it starts when that state requires another
     * Context; never execute a partial action under a stale ownership snapshot.
     */
    private void runScopedEntityTask(
        ChunkContextImpl expected, Entity entity, Runnable action, boolean interactive
    ) {
        if (closed || !expected.active()) return;
        ChunkContextImpl current = resolveOwner(entity);
        if (current == null) {
            action.run();
            return;
        }
        if (current != expected || !ownsAll(
            current.world(), entityTickScope(current, entity))) {
            if (!routeEntityTask(entity, action, interactive)) action.run();
            return;
        }
        action.run();
    }

    private static boolean ownsAll(WorldContextImpl world, long[] keys) {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || scope.primary().world() != world) return false;
        for (long key : keys) if (!scope.containsKey(key)) return false;
        return true;
    }

    /** Allocation-free validation of the entity's exact current mutable scope. */
    private static boolean ownsCurrentEntityTickScope(
        ChunkContextImpl owner, Entity entity
    ) {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || scope.primary().world() != owner.world()
            || !scope.containsKey(owner.key())) return false;
        if (entity instanceof EntityContextScopeBridge additional) {
            BlockPos position = additional.aerogel$additionalContextBlock(
                owner.world().level());
            if (position != null && !scope.containsKey(WorldContextImpl.key(
                position.getX() >> 4, position.getZ() >> 4))) return false;
        }

        AABB box = entity.getBoundingBox();
        if (box == null) return true;
        Vec3 movement = entity.getDeltaMovement();
        double moveX = movement == null || !Double.isFinite(movement.x)
            ? 0.0D : movement.x;
        double moveZ = movement == null || !Double.isFinite(movement.z)
            ? 0.0D : movement.z;
        double minX = Math.min(box.minX, box.minX + moveX);
        double minZ = Math.min(box.minZ, box.minZ + moveZ);
        double maxX = Math.max(box.maxX, box.maxX + moveX);
        double maxZ = Math.max(box.maxZ, box.maxZ + moveZ);
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) return true;

        int minChunkX = ((int) Math.floor(minX)) >> 4;
        int minChunkZ = ((int) Math.floor(minZ)) >> 4;
        int maxChunkX = ((int) Math.floor(maxX > minX
            ? Math.nextDown(maxX) : maxX)) >> 4;
        int maxChunkZ = ((int) Math.floor(maxZ > minZ
            ? Math.nextDown(maxZ) : maxZ)) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!scope.containsKey(WorldContextImpl.key(chunkX, chunkZ))) return false;
            }
        }
        return true;
    }

    /**
     * Routes an interaction through the exact union of the entity owner and the target
     * block's mutation scope. This keeps player state and target chunk state in one
     * ownership transaction even when the player reaches across a chunk boundary.
     */
    public boolean routeEntityBlockTask(
        Entity entity, ServerLevel level, BlockPos position, Runnable action
    ) {
        return routeEntityBlockTask(entity, level, position, action, false);
    }

    public boolean routeInteractiveEntityBlockTask(
        Entity entity, ServerLevel level, BlockPos position, Runnable action
    ) {
        return routeEntityBlockTask(entity, level, position, action, true);
    }

    private boolean routeEntityBlockTask(
        Entity entity, ServerLevel level, BlockPos position, Runnable action,
        boolean interactive
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        if (closed || entity.level() != level) return false;
        Runnable scopedAction = BlockInteractionScope.bind(level, position, action);

        ChunkContextImpl owner = resolveOwner(entity);
        if (owner == null || owner.world() != worldImpl(level)) return false;
        LongOpenHashSet keys = new LongOpenHashSet(blockMutationScope(position));
        keys.add(owner.key());
        long[] scopeKeys = keys.toLongArray();
        ContextThreadState.AccessScope currentScope = ContextThreadState.current();
        if (currentScope != null && currentScope.primary().world() == owner.world()) {
            boolean allOwned = true;
            for (long key : scopeKeys) {
                if (!currentScope.containsKey(key)) {
                    allOwned = false;
                    break;
                }
            }
            if (allOwned) return false;
        }

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeEntityBlockTask(
                    entity, level, position, scopedAction, interactive)) scopedAction.run();
            });
        };
        Runnable routed = () -> NativeTickCoordinator.runNative(
            List.of(entity), ignored -> {
                ChunkContextImpl current = resolveOwner(entity);
                if (current == owner) {
                    scopedAction.run();
                } else if (!routeEntityBlockTask(
                    entity, level, position, scopedAction, interactive)) {
                    scopedAction.run();
                }
            }, () -> { });
        boolean accepted = interactive
            ? owner.submitInteractiveNative(scopeKeys, routed, rejected,
                NativeTickCoordinator::taskRejected)
            : owner.submitNative(scopeKeys, routed, rejected,
                NativeTickCoordinator::taskRejected);
        if (!accepted) NativeTickCoordinator.taskRejected();
        return accepted;
    }

    /** Routes every entity-target interaction through both current owners. */
    public boolean routeInteractiveEntityTargetTask(
        Entity entity, Entity target, Runnable action
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        if (closed || entity.level() != target.level()) return false;

        ChunkContextImpl owner = resolveOwner(entity);
        ChunkContextImpl targetOwner = resolveOwner(target);
        if (owner == null || targetOwner == null || owner.world() != targetOwner.world()) {
            return false;
        }
        long[] scopeKeys = owner == targetOwner
            ? new long[] { owner.key() }
            : new long[] { owner.key(), targetOwner.key() };
        ContextThreadState.AccessScope currentScope = ContextThreadState.current();
        if (currentScope != null && currentScope.primary().world() == owner.world()
            && currentScope.containsKey(owner.key())
            && currentScope.containsKey(targetOwner.key())) return false;

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeInteractiveEntityTargetTask(entity, target, action)) action.run();
            });
        };
        boolean accepted = owner.submitInteractiveNative(scopeKeys, () ->
            NativeTickCoordinator.runNative(List.of(entity), ignored -> {
                ChunkContextImpl currentOwner = resolveOwner(entity);
                ChunkContextImpl currentTargetOwner = resolveOwner(target);
                if (currentOwner == owner && currentTargetOwner == targetOwner) {
                    action.run();
                } else if (!routeInteractiveEntityTargetTask(entity, target, action)) {
                    action.run();
                }
            }, () -> { }), rejected, NativeTickCoordinator::taskRejected);
        if (!accepted) NativeTickCoordinator.taskRejected();
        return accepted;
    }

    public boolean isEntityOwnerContext(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        ChunkContextImpl context = resolveOwner(entity);
        return context != null && context.current();
    }

    void runRouted(ChunkContextImpl expected, Entity entity, Runnable action) {
        runRouted(expected, entity, action, false);
    }

    private void runRouted(
        ChunkContextImpl expected, Entity entity, Runnable action, boolean interactive
    ) {
        if (closed || !expected.active()) return;
        ChunkContextImpl current = resolveOwner(entity);
        if (current == expected || current == null) {
            action.run();
        } else if (!routeEntityTask(entity, action, interactive)) {
            action.run();
        }
    }

    private ChunkContextImpl resolveOwner(Entity entity) {
        if (closed) return null;
        EntityContextOwnerBridge ownership = (EntityContextOwnerBridge) entity;
        Object observed = ownership.aerogel$contextOwner();
        if (!(entity.level() instanceof ServerLevel level)) {
            return observed instanceof ChunkContextImpl owned && owned.active() ? owned : null;
        }
        WorldContextImpl world = worlds.get(level);
        if (world == null) return null;
        if (observed instanceof ChunkContextImpl owned
            && owned.world() == world && owned.active()) return owned;
        ChunkPos position = entity.chunkPosition();
        ChunkContextImpl context = world.context(position.x(), position.z());
        if (ownership.aerogel$compareAndSetContextOwner(observed, context)) return context;
        Object raced = ownership.aerogel$contextOwner();
        return raced instanceof ChunkContextImpl owned
            && owned.world() == world && owned.active() ? owned : resolveOwner(entity);
    }

    private ChunkContextImpl resolveChunk(ServerLevel level, int chunkX, int chunkZ) {
        WorldContextImpl world = worlds.get(level);
        if (world == null) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk == null ? null : world.context(chunk);
    }

    private static final class TrackedRegistration {
        private final Entity entity;
        private final TrackedEntityBridge tracked;
        private final AtomicLong lastTick = new AtomicLong(Long.MIN_VALUE);
        private final AtomicBoolean dirtyPublicationPending = new AtomicBoolean();
        private volatile ChunkContextImpl owner;
        private volatile boolean publishing;

        private TrackedRegistration(
            Entity entity, TrackedEntityBridge tracked, ChunkContextImpl owner
        ) {
            this.entity = entity;
            this.tracked = tracked;
            this.owner = owner;
        }

        private boolean claim(long tick) {
            long observed = lastTick.get();
            while (observed < tick) {
                if (lastTick.compareAndSet(observed, tick)) return true;
                observed = lastTick.get();
            }
            return false;
        }
    }

    private static final class TickingRegistration {
        private final Entity entity;
        private final AtomicLong lastTick = new AtomicLong(Long.MIN_VALUE);
        private volatile ChunkContextImpl owner;

        private TickingRegistration(Entity entity, ChunkContextImpl owner) {
            this.entity = entity;
            this.owner = owner;
        }

        private boolean claim(long tick) {
            long observed = lastTick.get();
            while (observed < tick) {
                if (lastTick.compareAndSet(observed, tick)) return true;
                observed = lastTick.get();
            }
            return false;
        }
    }

    private static final class EntityBatch {
        private ChunkContextImpl context;
        private final List<Entity> entities = new ArrayList<>();

        private EntityBatch(ChunkContextImpl context) {
            this.context = context;
        }

    }

    private static final class BlockEntityBatch {
        private ChunkContextImpl context;
        private final List<TickingBlockEntity> blockEntities = new ArrayList<>();

        private BlockEntityBatch(ChunkContextImpl context) {
            this.context = context;
        }
    }

    private static final class OwnedTaskBatch {
        private ChunkContextImpl context;
        private final List<ContextOwnedEntityTask> tasks = new ArrayList<>();

        private OwnedTaskBatch(ChunkContextImpl context) {
            this.context = context;
        }
    }

    private static final class OwnedTaskBuffers {
        private final java.util.IdentityHashMap<ChunkContextImpl, OwnedTaskBatch> byContext =
            new java.util.IdentityHashMap<>();
        private final List<OwnedTaskBatch> batches = new ArrayList<>();
        private final List<OwnedTaskBatch> recycled = new ArrayList<>();
        private final List<ContextOwnedEntityTask> coordinatorTasks = new ArrayList<>();

        private void prepare() {
            coordinatorTasks.clear();
            batches.clear();
            for (OwnedTaskBatch batch : byContext.values()) {
                batch.context = null;
                batch.tasks.clear();
                recycled.add(batch);
            }
            byContext.clear();
        }

        private OwnedTaskBatch acquire(ChunkContextImpl context) {
            int last = recycled.size() - 1;
            OwnedTaskBatch batch = last < 0
                ? new OwnedTaskBatch(context)
                : recycled.remove(last);
            batch.context = context;
            return batch;
        }
    }

    private static final class BlockEntityBuffers {
        private final Long2ObjectOpenHashMap<BlockEntityBatch> byChunk =
            new Long2ObjectOpenHashMap<>();
        private final List<BlockEntityBatch> batches = new ArrayList<>();
        private final List<BlockEntityBatch> recycled = new ArrayList<>();

        private void prepare() {
            batches.clear();
            for (BlockEntityBatch batch : byChunk.values()) {
                batch.context = null;
                batch.blockEntities.clear();
                recycled.add(batch);
            }
            byChunk.clear();
        }

        private BlockEntityBatch acquire(ChunkContextImpl context) {
            int last = recycled.size() - 1;
            BlockEntityBatch batch = last < 0
                ? new BlockEntityBatch(context)
                : recycled.remove(last);
            batch.context = context;
            return batch;
        }
    }

    private static final class EntityBuffers {
        private final java.util.IdentityHashMap<ChunkContextImpl, EntityBatch> byContext =
            new java.util.IdentityHashMap<>();
        private final List<EntityBatch> batches = new ArrayList<>();
        private final List<EntityBatch> recycled = new ArrayList<>();
        private final List<Entity> coordinatorEntities = new ArrayList<>();

        private void prepare() {
            coordinatorEntities.clear();
            batches.clear();
            for (EntityBatch batch : byContext.values()) {
                batch.context = null;
                batch.entities.clear();
                recycled.add(batch);
            }
            byContext.clear();
        }

        private EntityBatch acquire(ChunkContextImpl context) {
            int last = recycled.size() - 1;
            EntityBatch batch = last < 0
                ? new EntityBatch(context)
                : recycled.remove(last);
            batch.context = context;
            return batch;
        }
    }

    /**
     * A lock-free lifetime counter for one autosave pass. Zero is closed with a
     * CAS, so a boundary movement either joins this pass or happens strictly
     * after it; there is no lost registration window.
     */
    private final class EntitySaveWave {
        private static final int CLOSED = -1;

        private final WorldContextImpl world;
        private final LongConsumer saveChunk;
        private final AtomicInteger pending;
        private final Set<EntityChunkPair> queuedPairs = ConcurrentHashMap.newKeySet();

        private EntitySaveWave(
            WorldContextImpl world, LongConsumer saveChunk, int initialTasks
        ) {
            this.world = world;
            this.saveChunk = saveChunk;
            this.pending = new AtomicInteger(initialTasks);
        }

        private void moved(long previousChunk, long currentChunk) {
            EntityChunkPair pair = EntityChunkPair.of(previousChunk, currentChunk);
            if (!queuedPairs.add(pair)) return;
            if (!tryRegister()) {
                queuedPairs.remove(pair);
                return;
            }
            queueEntitySave(this, new long[] { pair.first(), pair.second() }, pair);
        }

        private boolean tryRegister() {
            int current = pending.get();
            while (current != CLOSED) {
                if (pending.compareAndSet(current, current + 1)) return true;
                current = pending.get();
            }
            return false;
        }

        private void complete() {
            int current = pending.get();
            while (current > 0) {
                int updated = current - 1;
                if (!pending.compareAndSet(current, updated)) {
                    current = pending.get();
                    continue;
                }
                if (updated == 0 && pending.compareAndSet(0, CLOSED)) {
                    entitySaveWaves.remove(world, this);
                }
                return;
            }
        }

        private void stopAccepting() {
            int current = pending.get();
            while (current != CLOSED && !pending.compareAndSet(current, CLOSED)) {
                current = pending.get();
            }
        }
    }

    private record EntityChunkPair(long first, long second) {
        private static EntityChunkPair of(long left, long right) {
            return Long.compareUnsigned(left, right) <= 0
                ? new EntityChunkPair(left, right)
                : new EntityChunkPair(right, left);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Context scheduler is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        worlds.values().forEach(WorldContextImpl::close);
        worlds.clear();
        trackedEntities.clear();
        trackedByContext.clear();
        trackingViewers.clear();
        tickingEntities.clear();
        tickingByContext.clear();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.warning("Context workers did not drain within 10 seconds");
                workers.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }
}
