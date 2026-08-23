package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Exact, destination-owned distance field for Minecraft's {@code ChunkTracker}.
 *
 * <p>{@code ChunkTracker} propagates through all eight neighboring chunks at a
 * cost of one. With no blocked edges, its fixed point is exactly the minimum of
 * {@code clamp(sourceLevel) + max(abs(dx), abs(dz))}. Computing that closed form
 * removes the mutable global priority queue without changing the resulting
 * level. Each destination belongs to one stripe and only that stripe mutates its
 * contribution histogram, so parallel application needs neither a global lock
 * nor concurrent primitive maps.</p>
 */
public final class ExactChunkDistanceGraph {
    private final int maximumLevel;
    private final Stripe[] stripes;
    private final ConcurrentLinkedQueue<SourceUpdate> updates =
        new ConcurrentLinkedQueue<>();
    private final Long2IntOpenHashMap sources = new Long2IntOpenHashMap();
    private final Consumer<Runnable> asynchronousExecutor;
    private final ParallelDispatcher asynchronousDispatcher;
    private final Consumer<Runnable> completionExecutor;
    private final ConcurrentLinkedQueue<CompletedGeneration> completed =
        new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PublicationWaiter> publicationWaiters =
        new ConcurrentLinkedQueue<>();
    private final AtomicBoolean producerScheduled = new AtomicBoolean();
    private final AtomicLong submittedSequence = new AtomicLong();
    private volatile long publishedSequence;
    private volatile LevelPublisher asynchronousPublisher;

    public ExactChunkDistanceGraph(int levelCount, int ownerCount) {
        this(levelCount, ownerCount, null, null, null);
    }

    public ExactChunkDistanceGraph(
        int levelCount,
        int ownerCount,
        Consumer<Runnable> asynchronousExecutor,
        ParallelDispatcher asynchronousDispatcher
    ) {
        this(levelCount, ownerCount, asynchronousExecutor, asynchronousDispatcher,
            NativeTickCoordinator::submitGlobalCommit);
    }

    ExactChunkDistanceGraph(
        int levelCount,
        int ownerCount,
        Consumer<Runnable> asynchronousExecutor,
        ParallelDispatcher asynchronousDispatcher,
        Consumer<Runnable> completionExecutor
    ) {
        if (levelCount < 2 || levelCount > 256) {
            throw new IllegalArgumentException("levelCount must be in [2, 256]");
        }
        if (ownerCount < 1) {
            throw new IllegalArgumentException("ownerCount must be positive");
        }
        maximumLevel = levelCount - 1;
        sources.defaultReturnValue(maximumLevel);
        stripes = new Stripe[ownerCount];
        for (int index = 0; index < ownerCount; index++) {
            stripes[index] = new Stripe(maximumLevel);
        }
        this.asynchronousExecutor = asynchronousExecutor;
        this.asynchronousDispatcher = asynchronousDispatcher;
        this.completionExecutor = completionExecutor;
    }

    /**
     * Publishes the latest effective ticket level at one source chunk. Multiple
     * updates before the next drain are reduced to their final state, just as the
     * vanilla priority queue coalesces an unobserved intermediate edge value.
     */
    public void updateSource(long chunkKey, int level) {
        long sequence = submittedSequence.incrementAndGet();
        updates.offer(new SourceUpdate(chunkKey, clamp(level), sequence));
        scheduleProducer();
    }

    /** Applies every queued source delta and returns only destinations that changed. */
    public ChangeBatch apply(ParallelDispatcher dispatcher) {
        return applyGeneration(dispatcher).changes;
    }

    private CompletedGeneration applyGeneration(ParallelDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        // DistanceManager may ask several trackers for their already-published
        // state during one server tick. Avoid allocating a primitive map for
        // those overwhelmingly common empty drains. A producer racing after
        // this observation is handled by the next drain, exactly as it was
        // when poll() observed an empty queue below.
        if (updates.peek() == null) return CompletedGeneration.EMPTY;
        Long2IntOpenHashMap latest = new Long2IntOpenHashMap();
        latest.defaultReturnValue(maximumLevel);
        long sequence = 0L;
        SourceUpdate update;
        while ((update = updates.poll()) != null) {
            latest.put(update.chunkKey, update.level);
            sequence = Math.max(sequence, update.sequence);
        }
        if (latest.isEmpty()) return CompletedGeneration.EMPTY;

        LongArrayList keys = new LongArrayList(latest.size());
        IntArrayList previousLevels = new IntArrayList(latest.size());
        IntArrayList nextLevels = new IntArrayList(latest.size());
        for (var entry : latest.long2IntEntrySet()) {
            long key = entry.getLongKey();
            int previous = sources.get(key);
            int next = entry.getIntValue();
            if (previous == next) continue;
            keys.add(key);
            previousLevels.add(previous);
            nextLevels.add(next);
        }
        if (keys.isEmpty()) return new CompletedGeneration(ChangeBatch.EMPTY, sequence);

        StripeChanges[] changes = new StripeChanges[stripes.length];
        dispatcher.invoke(stripes.length, stripe -> changes[stripe] = stripes[stripe].apply(
            stripe, stripes.length, keys, previousLevels, nextLevels));

        for (int index = 0; index < keys.size(); index++) {
            long key = keys.getLong(index);
            int next = nextLevels.getInt(index);
            if (next == maximumLevel) sources.remove(key);
            else sources.put(key, next);
        }
        return new CompletedGeneration(new ChangeBatch(changes), sequence);
    }

    /** Publishes only fully completed immutable generations; this method never waits. */
    public int publishCompleted(LevelPublisher publisher) {
        asynchronousPublisher = Objects.requireNonNull(publisher, "publisher");
        int published = 0;
        CompletedGeneration generation;
        while ((generation = completed.poll()) != null) {
            published += publishGeneration(generation, publisher);
        }
        return published;
    }

    /** Completes on the publication thread after every update submitted so far is visible. */
    public CompletableFuture<Void> publicationAfterQueuedUpdates() {
        long target = submittedSequence.get();
        if (publishedSequence >= target) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PublicationWaiter waiter = new PublicationWaiter(target, completion);
        publicationWaiters.offer(waiter);
        if (publishedSequence >= target && publicationWaiters.remove(waiter)) {
            completion.complete(null);
        }
        return completion;
    }

    private void scheduleProducer() {
        if (asynchronousExecutor == null || !producerScheduled.compareAndSet(false, true)) return;
        asynchronousExecutor.accept(this::runProducer);
    }

    private void runProducer() {
        try {
            do {
                CompletedGeneration generation = applyGeneration(asynchronousDispatcher);
                if (generation.sequence != 0L) publishOrQueue(generation);
            } while (updates.peek() != null);
        } catch (Throwable error) {
            failPublicationWaiters(error);
            throw error;
        } finally {
            producerScheduled.set(false);
            if (updates.peek() != null) scheduleProducer();
        }
    }

    private void publishOrQueue(CompletedGeneration generation) {
        LevelPublisher publisher = asynchronousPublisher;
        if (publisher != null) {
            LevelPublisher ready = publisher;
            completionExecutor.accept(() -> publishGeneration(generation, ready));
            return;
        }
        completed.offer(generation);
        publisher = asynchronousPublisher;
        if (publisher != null && completed.remove(generation)) {
            LevelPublisher ready = publisher;
            completionExecutor.accept(() -> publishGeneration(generation, ready));
        }
    }

    private int publishGeneration(CompletedGeneration generation, LevelPublisher publisher) {
        int count = generation.changes.publish(publisher);
        publishedSequence = Math.max(publishedSequence, generation.sequence);
        completePublicationWaiters();
        return count;
    }

    private void completePublicationWaiters() {
        int candidates = publicationWaiters.size();
        for (int index = 0; index < candidates; index++) {
            PublicationWaiter waiter = publicationWaiters.poll();
            if (waiter == null) return;
            if (waiter.sequence <= publishedSequence) waiter.completion.complete(null);
            else publicationWaiters.offer(waiter);
        }
    }

    private void failPublicationWaiters(Throwable error) {
        PublicationWaiter waiter;
        while ((waiter = publicationWaiters.poll()) != null) {
            waiter.completion.completeExceptionally(error);
        }
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    private int clamp(int level) {
        return Math.max(0, Math.min(maximumLevel, level));
    }

    private static int owner(long key, int ownerCount) {
        long mixed = ConcurrentLong2ObjectMap.spread(key);
        return (int) Long.remainderUnsigned(mixed, ownerCount);
    }

    @FunctionalInterface
    public interface ParallelDispatcher {
        void invoke(int taskCount, IntConsumer task);
    }

    @FunctionalInterface
    public interface LevelPublisher {
        void publish(long chunkKey, int level);
    }

    public static final class ChangeBatch {
        private static final ChangeBatch EMPTY = new ChangeBatch(new StripeChanges[0]);
        private final StripeChanges[] stripes;

        private ChangeBatch(StripeChanges[] stripes) {
            this.stripes = stripes;
        }

        public int publish(LevelPublisher publisher) {
            Objects.requireNonNull(publisher, "publisher");
            int published = 0;
            for (StripeChanges stripe : stripes) {
                if (stripe == null) continue;
                for (int index = 0; index < stripe.keys.size(); index++) {
                    publisher.publish(stripe.keys.getLong(index),
                        stripe.levels.getByte(index) & 0xff);
                    published++;
                }
            }
            return published;
        }

        public boolean isEmpty() {
            for (StripeChanges stripe : stripes) {
                if (stripe != null && !stripe.keys.isEmpty()) return false;
            }
            return true;
        }
    }

    private static final class Stripe {
        private final int maximumLevel;
        private final Long2ObjectOpenHashMap<Destination> destinations =
            new Long2ObjectOpenHashMap<>();

        private Stripe(int maximumLevel) {
            this.maximumLevel = maximumLevel;
        }

        private StripeChanges apply(
            int owner, int ownerCount,
            LongArrayList sourceKeys,
            IntArrayList previousLevels,
            IntArrayList nextLevels
        ) {
            Long2ByteOpenHashMap originalLevels = new Long2ByteOpenHashMap();
            originalLevels.defaultReturnValue((byte) maximumLevel);

            for (int sourceIndex = 0; sourceIndex < sourceKeys.size(); sourceIndex++) {
                long sourceKey = sourceKeys.getLong(sourceIndex);
                int sourceX = (int) sourceKey;
                int sourceZ = (int) (sourceKey >>> 32);
                int previous = previousLevels.getInt(sourceIndex);
                int next = nextLevels.getInt(sourceIndex);
                int previousRadius = maximumLevel - previous - 1;
                int nextRadius = maximumLevel - next - 1;
                int radius = Math.max(previousRadius, nextRadius);

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int distance = Math.max(Math.abs(dx), Math.abs(dz));
                        int previousContribution = distance <= previousRadius
                            ? previous + distance : maximumLevel;
                        int nextContribution = distance <= nextRadius
                            ? next + distance : maximumLevel;
                        if (previousContribution == nextContribution) continue;

                        long targetKey = pack(sourceX + dx, sourceZ + dz);
                        if (ExactChunkDistanceGraph.owner(targetKey, ownerCount) != owner) {
                            continue;
                        }
                        Destination destination = destinations.get(targetKey);
                        int original = destination == null
                            ? maximumLevel : destination.level;
                        if (!originalLevels.containsKey(targetKey)) {
                            originalLevels.put(targetKey, (byte) original);
                        }
                        if (destination == null) {
                            destination = new Destination(maximumLevel);
                            destinations.put(targetKey, destination);
                        }
                        destination.replace(previousContribution, nextContribution);
                    }
                }
            }

            LongArrayList changedKeys = new LongArrayList();
            ByteArrayList changedLevels = new ByteArrayList();
            for (var entry : originalLevels.long2ByteEntrySet()) {
                long key = entry.getLongKey();
                int original = entry.getByteValue() & 0xff;
                Destination destination = destinations.get(key);
                int current = destination == null ? maximumLevel : destination.level;
                if (destination != null && current == maximumLevel) destinations.remove(key);
                if (current == original) continue;
                changedKeys.add(key);
                changedLevels.add((byte) current);
            }
            return new StripeChanges(changedKeys, changedLevels);
        }
    }

    private static final class Destination {
        private final int maximumLevel;
        private final int[] contributions;
        private int level;

        private Destination(int maximumLevel) {
            this.maximumLevel = maximumLevel;
            contributions = new int[maximumLevel];
            level = maximumLevel;
        }

        private void replace(int previous, int next) {
            if (previous < maximumLevel) {
                int count = contributions[previous];
                if (count == 0) {
                    throw new IllegalStateException(
                        "Missing distance contribution at level " + previous);
                }
                contributions[previous] = count - 1;
                if (level == previous && count == 1) {
                    level = maximumLevel;
                    for (int candidate = previous + 1;
                         candidate < maximumLevel; candidate++) {
                        if (contributions[candidate] != 0) {
                            level = candidate;
                            break;
                        }
                    }
                }
            }
            if (next < maximumLevel) {
                contributions[next]++;
                if (next < level) level = next;
            }
        }
    }

    private record SourceUpdate(long chunkKey, int level, long sequence) { }

    private record CompletedGeneration(ChangeBatch changes, long sequence) {
        private static final CompletedGeneration EMPTY =
            new CompletedGeneration(ChangeBatch.EMPTY, 0L);
    }

    private record PublicationWaiter(long sequence, CompletableFuture<Void> completion) { }

    private record StripeChanges(LongArrayList keys, ByteArrayList levels) { }

    static long pack(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ & 0xffffffffL) << 32;
    }
}
