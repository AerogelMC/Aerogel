package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent primitive-facing set for low-frequency cross-owner world indexes.
 *
 * <p>Packed chunk coordinates have a poor {@link Long#hashCode()} distribution:
 * many diagonal coordinates collapse into the same ConcurrentHashMap bin. Store a
 * bijectively mixed key internally and invert it at the iterator boundary, so this
 * changes neither membership nor iteration semantics.</p>
 */
public final class ConcurrentLongSet implements LongSet {
    private final Set<Long> values = ConcurrentHashMap.newKeySet();

    @Override
    public boolean add(long value) {
        return values.add(ConcurrentLong2ObjectMap.spread(value));
    }

    @Override
    public boolean contains(long value) {
        return values.contains(ConcurrentLong2ObjectMap.spread(value));
    }

    @Override
    public boolean remove(long value) {
        return values.remove(ConcurrentLong2ObjectMap.spread(value));
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void clear() {
        values.clear();
    }

    /**
     * Implement this explicitly instead of relying on the compile-time FastUtil
     * interface default. The FastUtil version shipped by the server declares it
     * abstract, so inheriting the stub default would otherwise fail at runtime.
     */
    @Override
    public long[] toLongArray() {
        long[] result = new long[values.size()];
        int index = 0;
        for (long mixed : values) {
            if (index == result.length) {
                int grownLength = Math.max(index + 1, result.length + Math.max(1, result.length >>> 1));
                result = Arrays.copyOf(result, grownLength);
            }
            result[index++] = ConcurrentLong2ObjectMap.unspread(mixed);
        }
        return index == result.length ? result : Arrays.copyOf(result, index);
    }

    /**
     * FastUtil 8.5.18 also declares this primitive array overload abstract.
     */
    public long[] toArray(long[] target) {
        long[] snapshot = toLongArray();
        if (target == null || target.length < snapshot.length) {
            return snapshot;
        }
        System.arraycopy(snapshot, 0, target, 0, snapshot.length);
        return target;
    }

    @Override
    public LongIterator iterator() {
        Iterator<Long> iterator = values.iterator();
        return new LongIterator() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public long nextLong() {
                return ConcurrentLong2ObjectMap.unspread(iterator.next());
            }
            @Override public void remove() { iterator.remove(); }
        };
    }
}
