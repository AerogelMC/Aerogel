package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Primitive concurrent map with processor-derived independent publication segments. Hot reads use
 * immutable fastutil generations; mutations of unrelated spatial keys never share a monitor.
 */
public final class ConcurrentLong2ObjectMap<V> extends Long2ObjectOpenHashMap<V> {
    private static final Object REMOVED = new Object();
    private final Segment<V>[] segments;
    private final int mask;
    private volatile V defaultValue;

    @SuppressWarnings("unchecked")
    public ConcurrentLong2ObjectMap() {
        int count = Integer.highestOneBit(Math.max(1, Runtime.getRuntime().availableProcessors()));
        segments = (Segment<V>[]) new Segment<?>[count];
        for (int index = 0; index < count; index++) segments[index] = new Segment<>();
        mask = count - 1;
    }

    @Override public V get(long key) {
        V value = segment(key).get(key);
        return value == null ? defaultValue : value;
    }
    @Override public V getOrDefault(long key, V fallback) {
        V value = segment(key).get(key);
        return value == null ? fallback : value;
    }
    @Override public boolean containsKey(long key) { return segment(key).get(key) != null; }
    @Override public V put(long key, V value) {
        return segment(key).put(key, Objects.requireNonNull(value, "value"));
    }
    @Override public V remove(long key) { return segment(key).remove(key); }
    @Override public V computeIfAbsent(long key, Long2ObjectFunction<? extends V> function) {
        return segment(key).computeIfAbsent(key, Objects.requireNonNull(function, "mappingFunction"));
    }

    @Override public LongSet keySet() {
        LongOpenHashSet result = new LongOpenHashSet();
        for (Segment<V> segment : segments) segment.snapshot().long2ObjectEntrySet().forEach(entry -> {
            if (entry.getValue().get() != null) result.add(entry.getLongKey());
        });
        return result;
    }

    @Override public ObjectCollection<V> values() {
        ObjectArrayList<V> result = new ObjectArrayList<>(java.util.List.of());
        for (Segment<V> segment : segments) segment.snapshot().values().forEach(cell -> {
            V value = cell.get();
            if (value != null) result.add(value);
        });
        return result;
    }

    @Override public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        Long2ObjectOpenHashMap<V> result = new Long2ObjectOpenHashMap<>();
        for (Segment<V> segment : segments) segment.snapshot().long2ObjectEntrySet().forEach(entry -> {
            V value = entry.getValue().get();
            if (value != null) result.put(entry.getLongKey(), value);
        });
        result.defaultReturnValue(defaultValue);
        return result.long2ObjectEntrySet();
    }

    @Override public int size() {
        int size = 0;
        for (Segment<V> segment : segments) {
            for (Cell<V> cell : segment.snapshot().values()) if (cell.get() != null) size++;
        }
        return size;
    }
    @Override public boolean isEmpty() {
        for (Segment<V> segment : segments) {
            for (Cell<V> cell : segment.snapshot().values()) if (cell.get() != null) return false;
        }
        return true;
    }
    @Override public void clear() { for (Segment<V> segment : segments) segment.clear(); }
    @Override public void defaultReturnValue(V value) { defaultValue = value; }
    @Override public V defaultReturnValue() { return defaultValue; }

    private Segment<V> segment(long key) {
        return segments[(int) spread(key) & mask];
    }

    private static final class Segment<V> {
        private volatile Long2ObjectOpenHashMap<Cell<V>> read = new Long2ObjectOpenHashMap<>();
        private Long2ObjectOpenHashMap<Cell<V>> writes;
        private volatile boolean amended;
        private int misses;

        V get(long key) {
            Cell<V> cell = read.get(key);
            if (cell == null && amended) synchronized (this) { cell = findLocked(key); }
            return cell == null ? null : cell.get();
        }

        V put(long key, V value) {
            Cell<V> cell = read.get(key);
            if (cell != null && cell.get() != null) return cell.set(value);
            synchronized (this) {
                cell = findLocked(key);
                if (cell != null) {
                    if (amended && writes.get(key) == null) writes.put(key, cell);
                    return cell.set(value);
                }
                if (!amended) beginWrites();
                writes.put(key, new Cell<>(value));
                return null;
            }
        }

        V remove(long key) {
            Cell<V> cell = read.get(key);
            if (cell != null) return cell.remove();
            if (!amended) return null;
            synchronized (this) {
                cell = read.get(key);
                if (cell != null) return cell.remove();
                if (!amended) return null;
                cell = writes.remove(key);
                return cell == null ? null : cell.remove();
            }
        }

        V computeIfAbsent(long key, Long2ObjectFunction<? extends V> function) {
            V existing = get(key);
            if (existing != null) return existing;
            synchronized (this) {
                Cell<V> cell = findLocked(key);
                existing = cell == null ? null : cell.get();
                if (existing != null) return existing;
                V created = Objects.requireNonNull(function.get(key), "mapped value");
                if (cell != null) {
                    if (amended && writes.get(key) == null) writes.put(key, cell);
                    cell.set(created);
                } else {
                    if (!amended) beginWrites();
                    writes.put(key, new Cell<>(created));
                }
                return created;
            }
        }

        synchronized Long2ObjectOpenHashMap<Cell<V>> snapshot() {
            if (amended) publish();
            return read;
        }

        synchronized void clear() {
            read = new Long2ObjectOpenHashMap<>();
            writes = null;
            amended = false;
            misses = 0;
        }

        private Cell<V> findLocked(long key) {
            Cell<V> cell = read.get(key);
            if (cell == null && amended) {
                cell = writes.get(key);
                if (++misses >= writes.size()) publish();
            }
            return cell;
        }

        private void beginWrites() {
            writes = new Long2ObjectOpenHashMap<>(read.size());
            read.long2ObjectEntrySet().forEach(entry -> {
                if (entry.getValue().get() != null) writes.put(entry.getLongKey(), entry.getValue());
            });
            amended = true;
        }

        private void publish() {
            read = writes;
            writes = null;
            amended = false;
            misses = 0;
        }
    }

    private static final class Cell<V> {
        private final AtomicReference<Object> value;
        Cell(V value) { this.value = new AtomicReference<>(value); }
        @SuppressWarnings("unchecked") V get() {
            Object current = value.get();
            return current == REMOVED ? null : (V) current;
        }
        @SuppressWarnings("unchecked") V set(V next) {
            Object previous = value.getAndSet(next);
            return previous == REMOVED ? null : (V) previous;
        }
        @SuppressWarnings("unchecked") V remove() {
            Object previous = value.getAndSet(REMOVED);
            return previous == REMOVED ? null : (V) previous;
        }
    }

    static long spread(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    static long unspread(long value) {
        value = (value ^ (value >>> 31) ^ (value >>> 62)) * 0x319642b2d24d8ec3L;
        value = (value ^ (value >>> 27) ^ (value >>> 54)) * 0x96de1b173f119089L;
        return value ^ (value >>> 30) ^ (value >>> 60);
    }
}
