package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.concurrent.ConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;

/**
 * Concurrent point access with a versioned primitive-key iteration image.
 *
 * <p>Chunk visibility changes at load boundaries, while tick eligibility reads the
 * same state repeatedly. Point reads use the concurrent source directly: rebuilding
 * every loaded status merely to answer one key turns a boundary transition into
 * quadratic copying. Operations that enumerate the map still rebuild only after an
 * actual publication and never lock a writer; a version change during copying simply
 * retries the exact iteration snapshot.</p>
 */
public final class PublishedLong2ObjectMap<V> extends Long2ObjectOpenHashMap<V> {
    private final ConcurrentHashMap<Long, V> writes = new ConcurrentHashMap<>();
    private final PaddedAtomicLong version = new PaddedAtomicLong();
    private volatile V defaultValue;
    private volatile LongConsumer changeListener = ignored -> { };
    private volatile ReadImage<V> image = new ReadImage<>(
        Long.MIN_VALUE, new Long2ObjectOpenHashMap<>());

    @Override
    public V get(long key) {
        V current = writes.get(ConcurrentLong2ObjectMap.spread(key));
        return current != null ? current : defaultValue;
    }

    @Override
    public V getOrDefault(long key, V fallback) {
        V current = writes.get(ConcurrentLong2ObjectMap.spread(key));
        return current != null ? current : fallback;
    }

    @Override
    public boolean containsKey(long key) {
        return writes.containsKey(ConcurrentLong2ObjectMap.spread(key));
    }

    @Override
    public V put(long key, V value) {
        V previous = writes.put(ConcurrentLong2ObjectMap.spread(key), value);
        if (previous != value) {
            version.incrementAndGet();
            changeListener.accept(key);
        }
        return previous;
    }

    @Override
    public V remove(long key) {
        V previous = writes.remove(ConcurrentLong2ObjectMap.spread(key));
        if (previous != null) {
            version.incrementAndGet();
            changeListener.accept(key);
        }
        return previous;
    }

    @Override
    public V computeIfAbsent(
        long key, Long2ObjectFunction<? extends V> mappingFunction
    ) {
        long spread = ConcurrentLong2ObjectMap.spread(key);
        V current = writes.get(spread);
        if (current != null) return current;
        V created = mappingFunction.get(key);
        V raced = writes.putIfAbsent(spread, created);
        if (raced != null) return raced;
        version.incrementAndGet();
        changeListener.accept(key);
        return created;
    }

    @Override
    public LongSet keySet() {
        LongOpenHashSet keys = new LongOpenHashSet();
        var iterator = readImage().keySet().iterator();
        while (iterator.hasNext()) keys.add(iterator.nextLong());
        return keys;
    }

    @Override
    public ObjectCollection<V> values() {
        return readImage().values();
    }

    /**
     * Fastutil does not derive its primitive entry set from {@link #keySet()}.
     * Vanilla uses {@code Long2ObjectMaps.fastIterable} while collecting every
     * loaded entity chunk that must be saved, so exposing the inherited (empty)
     * backing map here silently loses loaded-but-now-empty chunks.
     */
    @Override
    public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        return readImage().long2ObjectEntrySet();
    }

    @Override public int size() { return writes.size(); }

    @Override
    public void clear() {
        if (writes.isEmpty()) return;
        writes.clear();
        version.incrementAndGet();
    }

    @Override
    public void defaultReturnValue(V value) {
        defaultValue = value;
        version.incrementAndGet();
    }

    public void changeListener(LongConsumer listener) {
        changeListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    private Long2ObjectOpenHashMap<V> readImage() {
        while (true) {
            long observed = version.get();
            ReadImage<V> current = image;
            if (current.version == observed) return current.values;

            Long2ObjectOpenHashMap<V> updated = new Long2ObjectOpenHashMap<>(writes.size());
            V fallback = defaultValue;
            updated.defaultReturnValue(fallback);
            writes.forEach((mixed, value) -> updated.put(
                ConcurrentLong2ObjectMap.unspread(mixed.longValue()), value));
            if (version.get() != observed || defaultValue != fallback) continue;
            ReadImage<V> published = new ReadImage<>(observed, updated);
            image = published;
            return published.values;
        }
    }

    private record ReadImage<V>(long version, Long2ObjectOpenHashMap<V> values) { }
}
