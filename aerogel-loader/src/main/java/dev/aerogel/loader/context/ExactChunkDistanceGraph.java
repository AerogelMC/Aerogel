package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    public ExactChunkDistanceGraph(int levelCount, int ownerCount) {
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
    }

    /**
     * Publishes the latest effective ticket level at one source chunk. Multiple
     * updates before the next drain are reduced to their final state, just as the
     * vanilla priority queue coalesces an unobserved intermediate edge value.
     */
    public void updateSource(long chunkKey, int level) {
        updates.offer(new SourceUpdate(chunkKey, clamp(level)));
    }

    /** Applies every queued source delta and returns only destinations that changed. */
    public ChangeBatch apply(ParallelDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        // DistanceManager may ask several trackers for their already-published
        // state during one server tick. Avoid allocating a primitive map for
        // those overwhelmingly common empty drains. A producer racing after
        // this observation is handled by the next drain, exactly as it was
        // when poll() observed an empty queue below.
        if (updates.peek() == null) return ChangeBatch.EMPTY;
        Long2IntOpenHashMap latest = new Long2IntOpenHashMap();
        latest.defaultReturnValue(maximumLevel);
        SourceUpdate update;
        while ((update = updates.poll()) != null) {
            latest.put(update.chunkKey, update.level);
        }
        if (latest.isEmpty()) return ChangeBatch.EMPTY;

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
        if (keys.isEmpty()) return ChangeBatch.EMPTY;

        StripeChanges[] changes = new StripeChanges[stripes.length];
        dispatcher.invoke(stripes.length, stripe -> changes[stripe] = stripes[stripe].apply(
            stripe, stripes.length, keys, previousLevels, nextLevels));

        for (int index = 0; index < keys.size(); index++) {
            long key = keys.getLong(index);
            int next = nextLevels.getInt(index);
            if (next == maximumLevel) sources.remove(key);
            else sources.put(key, next);
        }
        return new ChangeBatch(changes);
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

    private record SourceUpdate(long chunkKey, int level) { }

    private record StripeChanges(LongArrayList keys, ByteArrayList levels) { }

    static long pack(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ & 0xffffffffL) << 32;
    }
}
