package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

import java.util.Objects;

/**
 * Primitive-key concurrent map optimized for world indexes that are populated
 * once and then read on every entity tick.
 *
 * <p>The published read generation is structurally immutable, so a hot lookup
 * is one primitive fastutil lookup with no monitor and no {@link Long} box.
 * Newly inserted keys are accumulated in a write generation. Once readers have
 * observed enough misses to cover that generation it is published atomically.
 * Existing entries share a small cell between generations and can therefore be
 * updated without copying the map or returning stale values.</p>
 */
public final class ConcurrentLong2ObjectMap<V> extends Long2ObjectOpenHashMap<V> {
    private static final Object REMOVED = new Object();

    private final Object writeLock = new Object();
    private volatile Long2ObjectOpenHashMap<Cell<V>> read =
        new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<Cell<V>> writes;
    private volatile boolean amended;
    private int misses;
    private volatile V defaultValue;

    @Override
    public V get(long key) {
        Cell<V> cell = findCell(key);
        V value = cell == null ? null : cell.get();
        return value == null ? defaultValue : value;
    }

    @Override
    public V getOrDefault(long key, V fallback) {
        Cell<V> cell = findCell(key);
        V value = cell == null ? null : cell.get();
        return value == null ? fallback : value;
    }

    @Override
    public boolean containsKey(long key) {
        Cell<V> cell = findCell(key);
        return cell != null && cell.get() != null;
    }

    @Override
    public V put(long key, V value) {
        Objects.requireNonNull(value, "value");

        Cell<V> cell = read.get(key);
        if (cell != null) {
            V current = cell.get();
            if (current != null) {
                return cell.set(value);
            }
            synchronized (writeLock) {
                // A removed cell can be absent from a concurrent write
                // generation. Reinsert the shared cell before resurrecting it.
                cell = read.get(key);
                if (cell != null) {
                    if (amended && writes.get(key) == null) {
                        writes.put(key, cell);
                    }
                    return cell.set(value);
                }
                if (amended) {
                    cell = writes.get(key);
                    if (cell != null) {
                        return cell.set(value);
                    }
                } else {
                    beginWriteGeneration();
                }
                writes.put(key, new Cell<>(value));
                return null;
            }
        }

        synchronized (writeLock) {
            cell = read.get(key);
            if (cell != null) {
                return cell.set(value);
            }
            if (amended) {
                cell = writes.get(key);
                if (cell != null) {
                    return cell.set(value);
                }
            } else {
                beginWriteGeneration();
            }
            writes.put(key, new Cell<>(value));
            return null;
        }
    }

    @Override
    public V remove(long key) {
        Cell<V> cell = read.get(key);
        if (cell != null) {
            return cell.remove();
        }

        if (!amended) {
            return null;
        }
        synchronized (writeLock) {
            cell = read.get(key);
            if (cell != null) {
                return cell.remove();
            }
            if (!amended) {
                return null;
            }
            cell = writes.remove(key);
            return cell == null ? null : cell.remove();
        }
    }

    @Override
    public V computeIfAbsent(
        long key, Long2ObjectFunction<? extends V> mappingFunction
    ) {
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        Cell<V> cell = findCell(key);
        V current = cell == null ? null : cell.get();
        if (current != null) {
            return current;
        }

        synchronized (writeLock) {
            cell = findCellLocked(key);
            current = cell == null ? null : cell.get();
            if (current != null) {
                return current;
            }
            V created = Objects.requireNonNull(mappingFunction.get(key), "mapped value");
            if (cell != null) {
                if (amended && writes.get(key) == null) {
                    writes.put(key, cell);
                }
                cell.set(created);
            } else {
                if (!amended) {
                    beginWriteGeneration();
                }
                writes.put(key, new Cell<>(created));
            }
            return created;
        }
    }

    @Override
    public LongSet keySet() {
        LongOpenHashSet snapshot = new LongOpenHashSet();
        Long2ObjectOpenHashMap<Cell<V>> generation = publishedGeneration();
        generation.keySet().forEach((long key) -> {
            Cell<V> cell = generation.get(key);
            if (cell != null && cell.get() != null) {
                snapshot.add(key);
            }
        });
        return snapshot;
    }

    @Override
    public ObjectCollection<V> values() {
        ObjectArrayList<V> snapshot = new ObjectArrayList<>(java.util.List.of());
        Long2ObjectOpenHashMap<Cell<V>> generation = publishedGeneration();
        generation.keySet().forEach((long key) -> {
            Cell<V> cell = generation.get(key);
            V value = cell == null ? null : cell.get();
            if (value != null) {
                snapshot.add(value);
            }
        });
        return snapshot;
    }

    @Override
    public int size() {
        int size = 0;
        Long2ObjectOpenHashMap<Cell<V>> generation = publishedGeneration();
        for (Cell<V> cell : generation.values()) {
            if (cell.get() != null) {
                size++;
            }
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        Long2ObjectOpenHashMap<Cell<V>> generation = publishedGeneration();
        for (Cell<V> cell : generation.values()) {
            if (cell.get() != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void clear() {
        synchronized (writeLock) {
            read = new Long2ObjectOpenHashMap<>();
            writes = null;
            amended = false;
            misses = 0;
        }
    }

    @Override public void defaultReturnValue(V value) { defaultValue = value; }
    @Override public V defaultReturnValue() { return defaultValue; }

    private Cell<V> findCell(long key) {
        Cell<V> cell = read.get(key);
        if (cell != null || !amended) {
            return cell;
        }
        synchronized (writeLock) {
            return findCellLocked(key);
        }
    }

    private Cell<V> findCellLocked(long key) {
        Cell<V> cell = read.get(key);
        if (cell == null && amended) {
            cell = writes.get(key);
            recordMiss();
        }
        return cell;
    }

    private void beginWriteGeneration() {
        Long2ObjectOpenHashMap<Cell<V>> next = new Long2ObjectOpenHashMap<>(read.size());
        read.keySet().forEach((long key) -> {
            Cell<V> cell = read.get(key);
            if (cell != null && cell.get() != null) {
                next.put(key, cell);
            }
        });
        writes = next;
        amended = true;
    }

    private void recordMiss() {
        if (++misses >= writes.size()) {
            publishWrites();
        }
    }

    private Long2ObjectOpenHashMap<Cell<V>> publishedGeneration() {
        synchronized (writeLock) {
            if (amended) {
                publishWrites();
            }
            return read;
        }
    }

    private void publishWrites() {
        read = writes;
        writes = null;
        amended = false;
        misses = 0;
    }

    /** A cell is shared by immutable map generations; only its value changes. */
    private static final class Cell<V> {
        private volatile Object value;

        private Cell(V value) { this.value = value; }

        @SuppressWarnings("unchecked")
        private V get() {
            Object current = value;
            return current == REMOVED ? null : (V) current;
        }

        @SuppressWarnings("unchecked")
        private synchronized V set(V next) {
            Object previous = value;
            value = next;
            return previous == REMOVED ? null : (V) previous;
        }

        @SuppressWarnings("unchecked")
        private synchronized V remove() {
            Object previous = value;
            value = REMOVED;
            return previous == REMOVED ? null : (V) previous;
        }
    }

    /**
     * SplitMix64's bijective finalizer remains shared with published maps that
     * still use boxed JDK concurrent indexes.
     */
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
