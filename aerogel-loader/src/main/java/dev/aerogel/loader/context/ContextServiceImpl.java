package dev.aerogel.loader.context;

import dev.aerogel.api.context.ChunkContext;
import dev.aerogel.api.context.ContextService;
import dev.aerogel.api.context.WorldContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
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
import net.minecraft.core.Direction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import dev.aerogel.loader.internal.EntityContextOwnerBridge;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ContextServiceImpl implements ContextService, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Aerogel-Contexts");
    private static final int DEFAULT_WORKERS = Math.max(
        2, Runtime.getRuntime().availableProcessors() - 1);

    private final ConcurrentHashMap<ServerLevel, WorldContextImpl> worlds =
        new ConcurrentHashMap<>();
    private final AtomicLong nextEpoch = new AtomicLong(1L);
    private final AtomicLong nextLease = new AtomicLong(1L);
    private final AtomicInteger workerIds = new AtomicInteger();
    private final ThreadLocal<EntityBuffers> entityBuffers =
        ThreadLocal.withInitial(EntityBuffers::new);
    private final ThreadLocal<BlockEntityBuffers> blockEntityBuffers =
        ThreadLocal.withInitial(BlockEntityBuffers::new);
    private final ThreadLocal<List<Runnable>> dispatchBatch = new ThreadLocal<>();
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
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory
                .newThread(pool);
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

    long nextEpoch() {
        return nextEpoch.getAndIncrement();
    }

    NeighborhoodLease newLease(ChunkContextImpl primary) {
        return new NeighborhoodLease(nextLease.getAndIncrement(), primary);
    }

    void dispatch(Runnable task) {
        if (closed) return;
        List<Runnable> batch = dispatchBatch.get();
        if (batch == null) workers.execute(task);
        else batch.add(task);
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
        if (context != null) context.close();
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
            long key = context.key();
            EntityBatch batch = buffers.byChunk.get(key);
            if (batch == null) {
                batch = buffers.acquire(context);
                buffers.byChunk.put(key, batch);
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

    public void tickChunks(
        ServerLevel level, ChunkMap chunkMap, Consumer<LevelChunk> action
    ) {
        WorldContextImpl world = worldImpl(level);
        dispatch(() -> dispatchBatched(() ->
            chunkMap.forEachBlockTickingChunk(chunk -> {
                world.context(chunk).chunkLane().offer(chunk, action);
            })
        ));
    }

    public void tickSpawningChunk(
        ServerLevel level, LevelChunk chunk, Runnable action
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(action, "action");
        worldImpl(level).context(chunk).chunkLane().offer(chunk, ignored -> action.run());
    }

    public void tickBlockEntities(
        ServerLevel level,
        List<TickingBlockEntity> tickers,
        boolean runsNormally
    ) {
        WorldContextImpl world = worldImpl(level);
        dispatch(() -> {
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
                long key = WorldContextImpl.key(position.getX() >> 4, position.getZ() >> 4);
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
                        } else if (runsNormally && level.shouldTickBlocksAt(ticker.getPos())) {
                            ticker.tick();
                        }
                    });
                }
            });
        });
    }

    public boolean routeChunkTask(ServerLevel level, LevelChunk chunk, Runnable action) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(action, "action");
        if (closed) return false;
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

    public boolean routeBlockTask(ServerLevel level, BlockPos position, Runnable action) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        if (closed) return false;
        WorldContextImpl world = worlds.get(level);
        ChunkContextImpl context = resolveChunk(level, position.getX() >> 4, position.getZ() >> 4);
        if (world == null || context == null) return false;
        long[] scopeKeys = blockMutationScope(position);
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
        ChunkContextImpl primary = context;

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeBlockTask(level, position, action)) action.run();
            });
        };
        boolean accepted = primary.submitNative(scopeKeys,
            () -> NativeTickCoordinator.runNative(
            List.of(action), Runnable::run, () -> { }), rejected);
        if (!accepted) {
            NativeTickCoordinator.taskRejected();
            return false;
        }
        return true;
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
                List.of(action), Runnable::run, () -> { }), rejected);
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
        keys.add(owner.key());
        AABB box = entity.getBoundingBox();
        if (box == null) return keys.toLongArray();
        Vec3 movement = entity.getDeltaMovement();
        double moveX = movement == null || !Double.isFinite(movement.x) ? 0.0D : movement.x;
        double moveZ = movement == null || !Double.isFinite(movement.z) ? 0.0D : movement.z;
        double minX = Math.min(box.minX, box.minX + moveX);
        double minZ = Math.min(box.minZ, box.minZ + moveZ);
        double maxX = Math.max(box.maxX, box.maxX + moveX);
        double maxZ = Math.max(box.maxZ, box.maxZ + moveZ);
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
            || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) return keys.toLongArray();

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
        return keys.toLongArray();
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
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(action, "action");
        if (closed) return false;

        ChunkContextImpl context = resolveOwner(entity);
        if (context == null || context.current()) return false;

        NativeTickCoordinator.taskSubmitted();
        Runnable rejected = () -> {
            NativeTickCoordinator.taskRejected();
            NativeTickCoordinator.submitMainThread(() -> {
                if (!routeEntityTask(entity, action)) action.run();
            });
        };
        boolean accepted = context.submitNative(() -> NativeTickCoordinator.runNative(
            List.of(entity), ignored -> runRouted(context, entity, action), () -> { }), rejected);
        if (!accepted) {
            NativeTickCoordinator.taskRejected();
            return false;
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
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        if (closed || entity.level() != level) return false;

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
                if (!routeEntityBlockTask(entity, level, position, action)) action.run();
            });
        };
        boolean accepted = owner.submitNative(scopeKeys, () -> NativeTickCoordinator.runNative(
            List.of(entity), ignored -> {
                ChunkContextImpl current = resolveOwner(entity);
                if (current == owner) {
                    action.run();
                } else if (!routeEntityBlockTask(entity, level, position, action)) {
                    action.run();
                }
            }, () -> { }), rejected);
        if (!accepted) NativeTickCoordinator.taskRejected();
        return accepted;
    }

    public boolean isEntityOwnerContext(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        ChunkContextImpl context = resolveOwner(entity);
        return context != null && context.current();
    }

    void runRouted(ChunkContextImpl expected, Entity entity, Runnable action) {
        ChunkContextImpl current = resolveOwner(entity);
        if (current == expected || current == null) {
            action.run();
        } else if (!routeEntityTask(entity, action)) {
            action.run();
        }
    }

    private ChunkContextImpl resolveOwner(Entity entity) {
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
        private final Long2ObjectOpenHashMap<EntityBatch> byChunk =
            new Long2ObjectOpenHashMap<>();
        private final List<EntityBatch> batches = new ArrayList<>();
        private final List<EntityBatch> recycled = new ArrayList<>();
        private final List<Entity> coordinatorEntities = new ArrayList<>();

        private void prepare() {
            coordinatorEntities.clear();
            batches.clear();
            for (EntityBatch batch : byChunk.values()) {
                batch.context = null;
                batch.entities.clear();
                recycled.add(batch);
            }
            byChunk.clear();
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

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Context scheduler is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        worlds.values().forEach(WorldContextImpl::close);
        worlds.clear();
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
