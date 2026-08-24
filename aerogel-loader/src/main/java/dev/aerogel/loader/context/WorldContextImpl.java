package dev.aerogel.loader.context;

import dev.aerogel.api.context.ChunkContext;
import dev.aerogel.api.context.WorldContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import dev.aerogel.loader.internal.ChunkContextBridge;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

final class WorldContextImpl implements WorldContext, AutoCloseable {
    private final ContextServiceImpl scheduler;
    private final ServerLevel level;
    private final ConcurrentHashMap<Long, ChunkContextImpl> contexts = new ConcurrentHashMap<>();
    private final PaddedAtomicReference<NaturalSpawnWindow> naturalSpawnWindow =
        new PaddedAtomicReference<>(NaturalSpawnWindow.EMPTY);
    private final LatestTickTaskLane entityTickProducer;
    private final LatestTickTaskLane trackingTickProducer;
    private final LatestTickTaskLane chunkTickProducer;
    private final LatestTickTaskLane blockEntityTickProducer;
    private final WorldCommitLane commitLane;
    private volatile boolean closed;
    private final PositionalRandomFactory chunkRandoms;

    WorldContextImpl(ContextServiceImpl scheduler, ServerLevel level) {
        this.scheduler = scheduler;
        this.level = level;
        this.entityTickProducer = new LatestTickTaskLane(scheduler);
        this.trackingTickProducer = new LatestTickTaskLane(scheduler);
        this.chunkTickProducer = new LatestTickTaskLane(scheduler);
        this.blockEntityTickProducer = new LatestTickTaskLane(scheduler);
        this.commitLane = new WorldCommitLane(scheduler);
        this.chunkRandoms = level == null ? null : RandomSource.create(level.getSeed())
            .forkPositional()
            .fromHashOf(level.dimension().identifier())
            .forkPositional();
    }

    @Override
    public ChunkContext chunk(int chunkX, int chunkZ) {
        return context(chunkX, chunkZ);
    }

    ChunkContextImpl context(int chunkX, int chunkZ) {
        if (closed) throw new IllegalStateException("World context is closed");
        long key = key(chunkX, chunkZ);
        long indexKey = ConcurrentLong2ObjectMap.spread(key);
        return contexts.compute(indexKey, (ignored, existing) ->
            existing != null && !existing.closed()
                ? existing
                : new ChunkContextImpl(this, scheduler, chunkX, chunkZ, key,
                    scheduler.nextEpoch()));
    }

    ChunkContextImpl existingContext(int chunkX, int chunkZ) {
        long key = ConcurrentLong2ObjectMap.spread(key(chunkX, chunkZ));
        ChunkContextImpl context = contexts.get(key);
        return context != null && context.active() ? context : null;
    }

    ChunkContextImpl context(LevelChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (chunk instanceof ChunkContextBridge bridge
            && bridge.aerogel$context() instanceof ChunkContextImpl direct
            && direct.world() == this) {
            return direct;
        }
        ChunkPos position = chunk.getPos();
        return context(position.x(), position.z());
    }

    void attach(LevelChunk chunk) {
        ChunkContextImpl context = context(chunk);
        if (chunk instanceof ChunkContextBridge bridge) bridge.aerogel$context(context);
    }

    void detach(LevelChunk chunk) {
        ChunkPos position = chunk.getPos();
        long key = key(position.x(), position.z());
        ChunkContextImpl removed = contexts.remove(
            ConcurrentLong2ObjectMap.spread(key));
        if (removed != null) removed.deactivate();
        if (chunk instanceof ChunkContextBridge bridge) bridge.aerogel$context(null);
    }

    boolean drainBeforeUnload(LevelChunk chunk, Runnable unload) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(unload, "unload");
        if (!(chunk instanceof ChunkContextBridge bridge)
            || !(bridge.aerogel$context() instanceof ChunkContextImpl context)
            || context.world() != this) return false;
        return context.drainThen(unload);
    }

    @Override
    public void executeGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (level.getServer().isSameThread()) task.run();
        else level.getServer().execute(task);
    }

    ServerLevel level() {
        return level;
    }

    LatestTickTaskLane entityTickProducer() { return entityTickProducer; }
    LatestTickTaskLane trackingTickProducer() { return trackingTickProducer; }
    LatestTickTaskLane chunkTickProducer() { return chunkTickProducer; }
    LatestTickTaskLane blockEntityTickProducer() { return blockEntityTickProducer; }
    WorldCommitLane commitLane() { return commitLane; }

    NaturalSpawnWave beginNaturalSpawnWave(NativeTickToken tickToken) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        NaturalSpawnWave created = new NaturalSpawnWave(this, completion, tickToken);
        while (!closed) {
            NaturalSpawnWindow observed = naturalSpawnWindow.get();
            NaturalSpawnWindow updated = observed.active == null
                ? new NaturalSpawnWindow(created, null)
                : new NaturalSpawnWindow(observed.active, created);
            if (!naturalSpawnWindow.compareAndSet(observed, updated)) continue;
            if (observed.pending != null) observed.pending.cancel();
            if (observed.active == null) created.activate();
            return created;
        }
        created.cancel();
        return created;
    }

    NaturalSpawnWave beginNaturalSpawnWave() {
        return beginNaturalSpawnWave(null);
    }

    void naturalSpawnWaveComplete(NaturalSpawnWave completed) {
        while (true) {
            NaturalSpawnWindow observed = naturalSpawnWindow.get();
            if (observed.active != completed) return;
            NaturalSpawnWave next = observed.pending;
            NaturalSpawnWindow updated = next == null
                ? NaturalSpawnWindow.EMPTY : new NaturalSpawnWindow(next, null);
            if (!naturalSpawnWindow.compareAndSet(observed, updated)) continue;
            if (next != null) next.activate();
            return;
        }
    }

    RandomSource randomFor(long chunkKey) {
        return chunkRandoms == null ? null : chunkRandoms.fromSeed(chunkKey);
    }

    static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ & 0xffffffffL) << 32;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        entityTickProducer.close();
        trackingTickProducer.close();
        chunkTickProducer.close();
        blockEntityTickProducer.close();
        commitLane.close();
        NaturalSpawnWindow waves = naturalSpawnWindow.getAndSet(NaturalSpawnWindow.EMPTY);
        if (waves.active != null) waves.active.cancel();
        if (waves.pending != null) waves.pending.cancel();
        contexts.values().forEach(ChunkContextImpl::deactivate);
        contexts.clear();
    }

    private record NaturalSpawnWindow(NaturalSpawnWave active, NaturalSpawnWave pending) {
        private static final NaturalSpawnWindow EMPTY = new NaturalSpawnWindow(null, null);
    }
}
