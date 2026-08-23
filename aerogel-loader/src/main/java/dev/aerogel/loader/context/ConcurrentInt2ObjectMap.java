package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.AbstractCollection;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

/**
 * Concurrent primitive-key publication map with a versioned entity image.
 *
 * <p>Point lookups use the concurrent source directly. Enumeration publishes one
 * immutable array only after actual membership changes, so natural-spawn passes
 * do not copy the complete entity index every tick. Writers never take a read
 * lock; a mutation observed while copying simply invalidates that candidate
 * image.</p>
 */
public final class ConcurrentInt2ObjectMap<V> extends Int2ObjectLinkedOpenHashMap<V> {
    private final ConcurrentHashMap<Integer, V> values = new ConcurrentHashMap<>();
    private final PaddedAtomicLong version = new PaddedAtomicLong();
    private volatile ReadImage<V> image = new ReadImage<>(
        Long.MIN_VALUE, new SnapshotValues<>(new Object[0]));

    @Override public V get(int key) { return values.get(key); }
    @Override public V put(int key, V value) {
        V previous = values.put(key, value);
        if (previous != value) version.incrementAndGet();
        return previous;
    }
    @Override public V remove(int key) {
        V previous = values.remove(key);
        if (previous != null) version.incrementAndGet();
        return previous;
    }
    @Override public boolean containsKey(int key) { return values.containsKey(key); }
    @Override public V computeIfAbsent(int key, IntFunction<? extends V> function) {
        V current = values.get(key);
        if (current != null) return current;
        V created = function.apply(key);
        if (created == null) return null;
        V raced = values.putIfAbsent(key, created);
        if (raced != null) return raced;
        version.incrementAndGet();
        return created;
    }
    @Override public V computeIfAbsent(
        int key, Int2ObjectFunction<? extends V> function
    ) {
        V current = values.get(key);
        if (current != null) return current;
        V created = function.get(key);
        if (created == null) return null;
        V raced = values.putIfAbsent(key, created);
        if (raced != null) return raced;
        version.incrementAndGet();
        return created;
    }
    @Override public ObjectCollection<V> values() {
        while (true) {
            long observed = version.get();
            ReadImage<V> current = image;
            if (current.version == observed) return current.values;
            Object[] elements = values.values().toArray();
            if (version.get() != observed) continue;
            ReadImage<V> updated = new ReadImage<>(
                observed, new SnapshotValues<>(elements));
            image = updated;
            return updated.values;
        }
    }
    @Override public int size() { return values.size(); }
    @Override public void clear() {
        if (values.isEmpty()) return;
        values.clear();
        version.incrementAndGet();
    }

    private record ReadImage<V>(long version, ObjectCollection<V> values) { }

    private static final class SnapshotValues<V>
        extends AbstractCollection<V> implements ObjectCollection<V> {
        private final Object[] elements;

        private SnapshotValues(Object[] elements) {
            this.elements = elements;
        }

        @Override
        public ObjectIterator<V> iterator() {
            return new ObjectIterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < elements.length;
                }

                @Override
                @SuppressWarnings("unchecked")
                public V next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return (V) elements[index++];
                }
            };
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public Object[] toArray() {
            return elements.clone();
        }
    }
}
