package dev.aerogel.loader.context;

import dev.aerogel.api.context.ChunkContext;
import dev.aerogel.api.context.ContextSnapshot;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ChunkContextImpl implements ChunkContext {
    private static final Logger LOGGER = Logger.getLogger("Aerogel-Contexts");
    private static final long UNMEASURED_TICK = Long.MIN_VALUE;

    private final WorldContextImpl world;
    private final ContextServiceImpl scheduler;
    private final int chunkX;
    private final int chunkZ;
    private final long key;
    private final long[] selfScope;
    private final long epoch;
    private final RandomSource random;
    private final NativeEntityLane entityLane;
    private final NativeChunkLane chunkLane;
    private final NativeBlockEntityLane blockEntityLane;
    private final ConcurrentLinkedQueue<ContextTask> snapshotMailbox =
        new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ContextTask> mailbox = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkContextImpl> waiters = new ConcurrentLinkedQueue<>();
    private final PaddedAtomicInteger queued = new PaddedAtomicInteger();
    private final PaddedAtomicBoolean scheduled = new PaddedAtomicBoolean();
    private final PaddedAtomicReference<NeighborhoodLease> ownership =
        new PaddedAtomicReference<>();
    private final PaddedAtomicReference<NeighborhoodLease> reservation =
        new PaddedAtomicReference<>();
    private final PaddedAtomicReference<CollectingNeighborUpdater> neighborUpdater =
        new PaddedAtomicReference<>();
    private final PaddedAtomicReference<Lifecycle> lifecycle =
        new PaddedAtomicReference<>(Lifecycle.ACTIVE);
    private final PaddedLongAdder submitted = new PaddedLongAdder();
    private final PaddedLongAdder completed = new PaddedLongAdder();
    private final PaddedLongAdder failed = new PaddedLongAdder();
    private final PaddedLongAdder stale = new PaddedLongAdder();
    private final PaddedLongAdder measuredTicks = new PaddedLongAdder();
    private final PaddedLongAdder totalExecutionNanos = new PaddedLongAdder();
    private final PaddedAtomicLong measurementTick =
        new PaddedAtomicLong(UNMEASURED_TICK);
    private final PaddedAtomicLong measurementTickExecutionNanos =
        new PaddedAtomicLong();
    private final PaddedLongAccumulator maximumExecutionNanos =
        new PaddedLongAccumulator(Long::max, 0L);

    ChunkContextImpl(
        WorldContextImpl world,
        ContextServiceImpl scheduler,
        int chunkX,
        int chunkZ,
        long key,
        long epoch
    ) {
        this.world = world;
        this.scheduler = scheduler;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.key = key;
        this.selfScope = new long[] { key };
        this.epoch = epoch;
        this.random = world.randomFor(key);
        this.entityLane = new NativeEntityLane(this);
        this.chunkLane = new NativeChunkLane(this);
        this.blockEntityLane = new NativeBlockEntityLane(this);
    }

    @Override public int chunkX() { return chunkX; }
    @Override public int chunkZ() { return chunkZ; }
    ContextServiceImpl scheduler() { return scheduler; }

    @Override
    public boolean current() {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        return scope != null && scope.contains(this);
    }

    @Override
    public void execute(Runnable task) {
        enqueue(selfScope, task, new CompletableFuture<>());
    }

    @Override
    public void executeNeighborhood(int radius, Runnable task) {
        enqueue(neighborhoodKeys(radius), task, new CompletableFuture<>());
    }

    @Override
    public void executeScope(Iterable<ChunkPos> chunks, Runnable task) {
        Objects.requireNonNull(chunks, "chunks");
        LongOpenHashSet keys = new LongOpenHashSet();
        keys.add(key);
        for (ChunkPos chunk : chunks) {
            Objects.requireNonNull(chunk, "chunk");
            keys.add(WorldContextImpl.key(chunk.x(), chunk.z()));
        }
        enqueue(keys.toLongArray(), task, new CompletableFuture<>());
    }

    CompletableFuture<Void> submit(int radius, Runnable task) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        enqueue(neighborhoodKeys(radius), task, completion);
        return completion;
    }

    CompletableFuture<Void> submit(long[] scopeKeys, Runnable task) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        enqueue(normalizeScope(scopeKeys), task, completion);
        return completion;
    }

    private void enqueue(long[] scopeKeys, Runnable task, CompletableFuture<Void> completion) {
        Objects.requireNonNull(task, "task");
        if (!active()) {
            throw new RejectedExecutionException("Chunk context is not active: " + chunkX + "," + chunkZ);
        }
        submitted.increment();
        queued.incrementAndGet();
        enqueueActive(mailbox, new ContextTask(epoch, scopeKeys, task, completion, null));
    }

    boolean submitNative(Runnable task, Runnable rejection) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(rejection, "rejection");
        if (!active()) return false;
        submitted.increment();
        queued.incrementAndGet();
        enqueueActive(mailbox, new ContextTask(epoch, selfScope, task, null, rejection));
        return true;
    }

    boolean submitSnapshot(Runnable task, Runnable rejection) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(rejection, "rejection");
        if (!active()) return false;
        submitted.increment();
        queued.incrementAndGet();
        enqueueActive(snapshotMailbox,
            new ContextTask(epoch, selfScope, task, null, rejection));
        return true;
    }

    boolean submitNative(long[] scopeKeys, Runnable task, Runnable rejection) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(rejection, "rejection");
        if (!active()) {
            return false;
        }
        submitted.increment();
        queued.incrementAndGet();
        enqueueActive(mailbox, new ContextTask(
            epoch, normalizeScope(scopeKeys), task, null, rejection));
        return true;
    }

    /**
     * Completes the lock-free hand-off between submission and deactivation.
     *
     * A submitter may observe ACTIVE immediately before deactivate drains the
     * mailbox. Rechecking after publication lets that submitter reclaim and reject
     * its own task when the deactivator missed it. If removal fails, either the
     * deactivator or the sole Context consumer already owns the task.
     */
    private void enqueueActive(
        ConcurrentLinkedQueue<ContextTask> target, ContextTask contextTask
    ) {
        target.add(contextTask);
        if (!active() && target.remove(contextTask)) {
            queued.decrementAndGet();
            rejectStale(contextTask);
            return;
        }
        schedule();
    }

    private long[] neighborhoodKeys(int radius) {
        if (radius < 0) throw new IllegalArgumentException("radius must not be negative");
        if (radius == 0) return selfScope;
        int diameter = Math.addExact(Math.multiplyExact(radius, 2), 1);
        long count = (long) diameter * diameter;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("neighborhood contains too many chunks");
        }
        long[] keys = new long[(int) count];
        int index = 0;
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                keys[index++] = WorldContextImpl.key(x, z);
            }
        }
        return keys;
    }

    private long[] normalizeScope(long[] requested) {
        Objects.requireNonNull(requested, "scopeKeys");
        LongOpenHashSet keys = new LongOpenHashSet(requested);
        keys.add(key);
        return keys.toLongArray();
    }

    private void schedule() {
        NeighborhoodLease reserved = reservation.get();
        if (reserved != null && reserved.primary() != this) return;
        if (!closed() && scheduled.compareAndSet(false, true)) {
            scheduler.dispatch(this::drain);
        }
    }

    private void drain() {
        NeighborhoodLease reserved = reservation.get();
        if (reserved != null && reserved.primary() != this) {
            scheduled.set(false);
            reserved.primary().schedule();
            if (reservation.get() == null && hasTasks()) schedule();
            return;
        }
        NeighborhoodLease self = scheduler.newLease(this);
        if (!ownership.compareAndSet(null, self)) {
            scheduled.set(false);
            if (ownership.get() == null && hasTasks()) schedule();
            return;
        }

        try {
            while (!closed()) {
                NeighborhoodLease pendingReservation = reservation.get();
                if (pendingReservation != null && pendingReservation.primary() != this) {
                    pendingReservation.primary().schedule();
                    break;
                }
                ContextTask task = snapshotMailbox.poll();
                if (task == null) task = mailbox.poll();
                if (task == null) break;
                queued.decrementAndGet();
                if (task.epoch() != epoch) {
                    rejectStale(task);
                    continue;
                }
                if (task.scopeKeys() == selfScope
                    || task.scopeKeys().length == 1 && task.scopeKeys()[0] == key) {
                    runOwned(task, null);
                } else if (!runNeighborhood(task, self)) {
                    queued.incrementAndGet();
                    mailbox.add(task);
                    break;
                }
            }
        } finally {
            ownership.compareAndSet(self, null);
            wakeWaiters();
            scheduled.set(false);
            if (hasTasks() && !closed()) schedule();
        }
    }

    private boolean runNeighborhood(ContextTask task, NeighborhoodLease self) {
        long[] scopeKeys = task.scopeKeys();
        List<ChunkContextImpl> affected = new ArrayList<>(scopeKeys.length);
        for (long scopeKey : scopeKeys) {
            affected.add(world.context(ChunkPos.getX(scopeKey), ChunkPos.getZ(scopeKey)));
        }
        affected.sort(Comparator.comparingLong(ChunkContextImpl::canonicalKey));

        NeighborhoodLease neighborhood = task.neighborhoodLease(this, scheduler);
        if (!ownership.compareAndSet(self, neighborhood)) {
            throw new IllegalStateException("Primary context lost ownership");
        }
        List<ChunkContextImpl> newlyReserved = new ArrayList<>(affected.size());
        ChunkContextImpl conflict = null;
        for (ChunkContextImpl context : affected) {
            NeighborhoodLease existing = context.reservation.get();
            if (existing == neighborhood) continue;
            if (existing != null
                || !context.reservation.compareAndSet(null, neighborhood)) {
                conflict = context;
                break;
            }
            newlyReserved.add(context);
        }

        if (conflict != null) {
            for (int i = newlyReserved.size() - 1; i >= 0; i--) {
                newlyReserved.get(i).clearReservation(neighborhood);
            }
            if (!ownership.compareAndSet(neighborhood, self)) {
                throw new IllegalStateException("Primary neighborhood ownership was corrupted");
            }
            conflict.addWaiter(this);
            return false;
        }

        List<ChunkContextImpl> acquired = new ArrayList<>(affected.size() - 1);
        for (ChunkContextImpl context : affected) {
            if (context == this) continue;
            if (!context.ownership.compareAndSet(null, neighborhood)) {
                conflict = context;
                break;
            }
            acquired.add(context);
        }

        if (conflict != null) {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).release(neighborhood);
            }
            if (!ownership.compareAndSet(neighborhood, self)) {
                throw new IllegalStateException("Primary neighborhood ownership was corrupted");
            }
            conflict.addWaiter(this);
            return false;
        }

        try {
            runOwned(task, new LongOpenHashSet(scopeKeys));
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).release(neighborhood);
            }
            if (!ownership.compareAndSet(neighborhood, self)) {
                throw new IllegalStateException("Primary neighborhood ownership was corrupted");
            }
            for (int i = affected.size() - 1; i >= 0; i--) {
                affected.get(i).clearReservation(neighborhood);
            }
        }
        return true;
    }

    private void clearReservation(NeighborhoodLease lease) {
        if (!reservation.compareAndSet(lease, null)) {
            throw new IllegalStateException("Neighborhood reservation release mismatch");
        }
        if (hasTasks()) schedule();
    }

    private void runOwned(ContextTask task, LongOpenHashSet ownedKeys) {
        long started = System.nanoTime();
        long serverTick = NativeTickCoordinator.currentServerTick();
        ContextThreadState.enter(new ContextThreadState.AccessScope(this, ownedKeys));
        try {
            task.action().run();
            completed.increment();
            complete(task, null);
        } catch (Throwable error) {
            failed.increment();
            complete(task, error);
            LOGGER.log(Level.SEVERE,
                "Chunk context task failed at " + chunkX + "," + chunkZ, error);
        } finally {
            ContextThreadState.leave();
            long elapsed = System.nanoTime() - started;
            totalExecutionNanos.add(elapsed);
            recordTickExecution(serverTick, elapsed);
        }
    }

    private void recordTickExecution(long serverTick, long elapsed) {
        long observedTick = measurementTick.get();
        if (observedTick != serverTick) {
            long completedTickNanos = measurementTickExecutionNanos.getAndSet(0L);
            if (observedTick != UNMEASURED_TICK) {
                measuredTicks.increment();
                maximumExecutionNanos.accumulate(completedTickNanos);
            }
            measurementTick.set(serverTick);
        }
        measurementTickExecutionNanos.addAndGet(elapsed);
    }

    private void addWaiter(ChunkContextImpl waiter) {
        waiters.add(waiter);
        if (ownership.get() == null) wakeWaiters();
    }

    private void release(NeighborhoodLease lease) {
        if (!ownership.compareAndSet(lease, null)) {
            throw new IllegalStateException("Neighborhood lease release mismatch");
        }
        wakeWaiters();
        if (hasTasks()) schedule();
    }

    private void wakeWaiters() {
        ChunkContextImpl waiter;
        while ((waiter = waiters.poll()) != null) waiter.schedule();
    }

    private void rejectStale(ContextTask task) {
        stale.increment();
        if (task.rejection() != null) task.rejection().run();
        complete(task, new RejectedExecutionException(
            "Stale chunk context task for epoch " + task.epoch()));
    }

    private void complete(ContextTask task, Throwable error) {
        if (task.completion() == null) return;
        if (error == null) {
            task.completion().complete(null);
        } else {
            task.completion().completeExceptionally(error);
        }
    }

    @Override
    public void assertCurrent() {
        if (!current()) {
            throw new IllegalStateException(
                "Chunk " + chunkX + "," + chunkZ + " is outside the current ownership scope");
        }
    }

    @Override
    public ContextSnapshot snapshot() {
        long currentTick = measurementTick.get();
        long currentTickExecutionNanos = measurementTickExecutionNanos.get();
        long measuredTickCount = measuredTicks.sum()
            + (currentTick == UNMEASURED_TICK ? 0L : 1L);
        return new ContextSnapshot(
            epoch,
            lifecycle.get().name(),
            submitted.sum(),
            completed.sum(),
            failed.sum(),
            stale.sum(),
            measuredTickCount,
            totalExecutionNanos.sum(),
            Math.max(maximumExecutionNanos.get(), currentTickExecutionNanos),
            Math.max(0, queued.get()));
    }

    boolean active() {
        return lifecycle.get() == Lifecycle.ACTIVE;
    }

    boolean closed() {
        return lifecycle.get() == Lifecycle.CLOSED;
    }

    boolean drainThen(Runnable commit) {
        Objects.requireNonNull(commit, "commit");
        if (!lifecycle.compareAndSet(Lifecycle.ACTIVE, Lifecycle.DRAINING)) {
            return lifecycle.get() == Lifecycle.DRAINING;
        }
        submitted.increment();
        queued.incrementAndGet();
        mailbox.add(new ContextTask(epoch, selfScope, () -> {
            lifecycle.set(Lifecycle.CLOSED);
            NativeTickCoordinator.submitMainThread(commit);
        }, null, null));
        schedule();
        return true;
    }

    void deactivate() {
        if (!lifecycle.compareAndSet(Lifecycle.ACTIVE, Lifecycle.DRAINING)) return;
        ContextTask task;
        while ((task = snapshotMailbox.poll()) != null) {
            queued.decrementAndGet();
            rejectStale(task);
        }
        while ((task = mailbox.poll()) != null) {
            queued.decrementAndGet();
            rejectStale(task);
        }
        lifecycle.set(Lifecycle.CLOSED);
        wakeWaiters();
    }

    private boolean hasTasks() {
        return !snapshotMailbox.isEmpty() || !mailbox.isEmpty();
    }

    WorldContextImpl world() { return world; }
    RandomSource random() { return random; }
    CollectingNeighborUpdater neighborUpdater(
        net.minecraft.world.level.Level level, int maximumChainedUpdates
    ) {
        CollectingNeighborUpdater current = neighborUpdater.get();
        if (current != null) return current;
        CollectingNeighborUpdater created =
            new CollectingNeighborUpdater(level, maximumChainedUpdates);
        return neighborUpdater.compareAndSet(null, created) ? created : neighborUpdater.get();
    }
    NativeEntityLane entityLane() { return entityLane; }
    NativeChunkLane chunkLane() { return chunkLane; }
    NativeBlockEntityLane blockEntityLane() { return blockEntityLane; }
    void runEntity(Entity entity, java.util.function.Consumer<Entity> action) {
        scheduler.runRouted(this, entity, () -> action.accept(entity));
    }
    long key() { return key; }
    long canonicalKey() { return key ^ Long.MIN_VALUE; }
    boolean reservedFor(ChunkContextImpl primary) {
        NeighborhoodLease current = reservation.get();
        return current != null && current.primary() == primary;
    }

    private enum Lifecycle {
        ACTIVE,
        DRAINING,
        CLOSED
    }
}
