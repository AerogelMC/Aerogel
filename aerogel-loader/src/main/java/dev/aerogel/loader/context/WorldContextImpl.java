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
    private final PaddedAtomicReference<CompletableFuture<Void>> naturalSpawnTail =
        new PaddedAtomicReference<>(CompletableFuture.completedFuture(null));
    private volatile boolean closed;
    private final PositionalRandomFactory chunkRandoms;

    WorldContextImpl(ContextServiceImpl scheduler, ServerLevel level) {
        this.scheduler = scheduler;
        this.level = level;
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
        return contexts.compute(key, (ignored, existing) ->
            existing != null && !existing.closed()
                ? existing
                : new ChunkContextImpl(this, scheduler, chunkX, chunkZ, key,
                    scheduler.nextEpoch()));
    }

    ChunkContextImpl existingContext(int chunkX, int chunkZ) {
        ChunkContextImpl context = contexts.get(key(chunkX, chunkZ));
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
        ChunkContextImpl removed = contexts.remove(key);
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

    NaturalSpawnWave beginNaturalSpawnWave() {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        CompletableFuture<Void> predecessor = naturalSpawnTail.getAndSet(completion);
        return new NaturalSpawnWave(predecessor, completion);
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
        contexts.values().forEach(ChunkContextImpl::deactivate);
        contexts.clear();
    }
}
