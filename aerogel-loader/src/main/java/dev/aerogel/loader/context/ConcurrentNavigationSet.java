package dev.aerogel.loader.context;

import dev.aerogel.loader.internal.PathNavigationBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent tracked-mob set with an exact, chunk-addressed navigation influence index.
 *
 * <p>Vanilla checks every tracked mob for every collision-shape change. Its final
 * predicate can only succeed inside the sphere derived from the mob position, path
 * end, and remaining node count. This set indexes the conservative chunk projection
 * of that exact sphere, then leaves the final vanilla predicate untouched.</p>
 */
public final class ConcurrentNavigationSet extends AbstractSet<Mob> {
    private static final long[] NO_CHUNKS = new long[0];

    private final ConcurrentHashMap<Mob, Entry> members = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Mob, Boolean>> byChunk =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Mob, Boolean> transitioning = new ConcurrentHashMap<>();

    @Override
    public boolean add(Mob mob) {
        if (mob == null) throw new NullPointerException("mob");
        transitioning.put(mob, Boolean.TRUE);
        Entry created = entry(mob);
        Entry previous = members.putIfAbsent(mob, created);
        if (previous == null) addToBuckets(mob, created.chunks);
        transitioning.remove(mob);
        return previous == null;
    }

    @Override
    public boolean remove(Object value) {
        if (!(value instanceof Mob mob)) return false;
        transitioning.put(mob, Boolean.TRUE);
        Entry previous = members.remove(mob);
        if (previous != null) removeFromBuckets(mob, previous.chunks);
        transitioning.remove(mob);
        return previous != null;
    }

    @Override public boolean contains(Object value) { return members.containsKey(value); }
    @Override public int size() { return members.size(); }
    @Override public boolean isEmpty() { return members.isEmpty(); }

    @Override
    public void clear() {
        for (Mob mob : members.keySet()) transitioning.put(mob, Boolean.TRUE);
        members.clear();
        byChunk.clear();
        transitioning.clear();
    }

    @Override
    public Iterator<Mob> iterator() {
        return members.keySet().iterator();
    }

    /** Keeps the mob in the exact fallback set while its position or path mutates. */
    public void beginUpdate(Mob mob) {
        if (members.containsKey(mob)) transitioning.put(mob, Boolean.TRUE);
    }

    /** Publishes the new exact projection before removing the old one. */
    public void finishUpdate(Mob mob) {
        Entry previous = members.get(mob);
        if (previous == null) {
            transitioning.remove(mob);
            return;
        }
        Entry updated = entry(mob);
        if (!Arrays.equals(previous.chunks, updated.chunks)) {
            addToBuckets(mob, updated.chunks);
            if (members.replace(mob, previous, updated)) {
                removeFromBucketsExcept(mob, previous.chunks, updated.chunks);
            } else {
                removeFromBucketsExcept(mob, updated.chunks,
                    members.getOrDefault(mob, Entry.NONE).chunks);
            }
        }
        transitioning.remove(mob);
    }

    public Iterator<Mob> candidates(BlockPos position) {
        ConcurrentHashMap<Mob, Boolean> bucket = byChunk.get(ChunkPos.pack(position));
        if (transitioning.isEmpty()) {
            return bucket == null ? Collections.emptyIterator()
                : liveIterator(bucket.keySet().iterator());
        }
        Set<Mob> result = Collections.newSetFromMap(new IdentityHashMap<>());
        if (bucket != null) result.addAll(bucket.keySet());
        result.addAll(transitioning.keySet());
        result.removeIf(mob -> !members.containsKey(mob));
        return result.iterator();
    }

    public Collection<Mob> candidates(Iterable<BlockPos> positions) {
        Set<Mob> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos position : positions) {
            if (position == null) continue;
            Iterator<Mob> candidates = candidates(position);
            while (candidates.hasNext()) result.add(candidates.next());
        }
        return new ArrayList<>(result);
    }

    private Iterator<Mob> liveIterator(Iterator<Mob> source) {
        return new Iterator<>() {
            private Mob next;

            @Override
            public boolean hasNext() {
                while (next == null && source.hasNext()) {
                    Mob candidate = source.next();
                    if (members.containsKey(candidate)) next = candidate;
                }
                return next != null;
            }

            @Override
            public Mob next() {
                if (!hasNext()) throw new NoSuchElementException();
                Mob result = next;
                next = null;
                return result;
            }
        };
    }

    private void addToBuckets(Mob mob, long[] chunks) {
        for (long chunk : chunks) {
            byChunk.computeIfAbsent(chunk, ignored -> new ConcurrentHashMap<>())
                .put(mob, Boolean.TRUE);
        }
    }

    private void removeFromBuckets(Mob mob, long[] chunks) {
        for (long chunk : chunks) {
            ConcurrentHashMap<Mob, Boolean> bucket = byChunk.get(chunk);
            if (bucket == null) continue;
            bucket.remove(mob);
            if (bucket.isEmpty()) byChunk.remove(chunk, bucket);
        }
    }

    private void removeFromBucketsExcept(Mob mob, long[] chunks, long[] retained) {
        for (long chunk : chunks) {
            if (Arrays.binarySearch(retained, chunk) >= 0) continue;
            ConcurrentHashMap<Mob, Boolean> bucket = byChunk.get(chunk);
            if (bucket == null) continue;
            bucket.remove(mob);
            if (bucket.isEmpty()) byChunk.remove(chunk, bucket);
        }
    }

    private static Entry entry(Mob mob) {
        PathNavigation navigation = mob.getNavigation();
        PathNavigationBridge bridge = (PathNavigationBridge) navigation;
        if (bridge.aerogel$hasDelayedRecomputation()) return Entry.NONE;
        Path path = bridge.aerogel$path();
        if (path == null || path.isDone() || path.getNodeCount() == 0) return Entry.NONE;
        int remaining = path.getNodeCount() - path.getNextNodeIndex();
        if (remaining <= 0) return Entry.NONE;
        Node end = path.getEndNode();
        if (end == null) return Entry.NONE;

        double centerX = (end.x + mob.getX()) * 0.5D;
        double centerZ = (end.z + mob.getZ()) * 0.5D;
        int minChunkX = floorToInt(centerX - remaining - 0.5D) >> 4;
        int maxChunkX = floorToInt(centerX + remaining - 0.5D) >> 4;
        int minChunkZ = floorToInt(centerZ - remaining - 0.5D) >> 4;
        int maxChunkZ = floorToInt(centerZ + remaining - 0.5D) >> 4;
        long count = (long) maxChunkX - minChunkX + 1L;
        count *= (long) maxChunkZ - minChunkZ + 1L;
        if (count <= 0L || count > Integer.MAX_VALUE) {
            throw new IllegalStateException("Navigation influence exceeds addressable chunks");
        }
        long[] chunks = new long[(int) count];
        int index = 0;
        for (int x = minChunkX; ; x++) {
            for (int z = minChunkZ; ; z++) {
                chunks[index++] = ChunkPos.pack(x, z);
                if (z == maxChunkZ) break;
            }
            if (x == maxChunkX) break;
        }
        Arrays.sort(chunks);
        return new Entry(chunks);
    }

    private static int floorToInt(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private record Entry(long[] chunks) {
        private static final Entry NONE = new Entry(NO_CHUNKS);
    }
}
