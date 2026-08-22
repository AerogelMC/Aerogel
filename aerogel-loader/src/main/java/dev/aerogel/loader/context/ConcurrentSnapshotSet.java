package dev.aerogel.loader.context;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent membership with a compact, versioned traversal image.
 *
 * <p>Navigation membership changes comparatively rarely, while block updates may
 * traverse the set tens of thousands of times in one command. A version check makes
 * unchanged traversals array-dense without locking writers or retaining a stale image
 * after a membership change. The version counter is cache-line isolated because every
 * Context may publish navigation changes concurrently.
 */
public final class ConcurrentSnapshotSet<E> extends AbstractSet<E> {
    private final ConcurrentHashMap<E, Boolean> members = new ConcurrentHashMap<>();
    private final PaddedAtomicLong version = new PaddedAtomicLong();
    private volatile Snapshot snapshot = new Snapshot(Long.MIN_VALUE, new Object[0]);

    @Override
    public boolean add(E element) {
        Objects.requireNonNull(element, "element");
        if (members.putIfAbsent(element, Boolean.TRUE) != null) return false;
        version.incrementAndGet();
        return true;
    }

    @Override
    public boolean remove(Object element) {
        if (members.remove(element) == null) return false;
        version.incrementAndGet();
        return true;
    }

    @Override
    public boolean contains(Object element) {
        return members.containsKey(element);
    }

    @Override
    public int size() {
        return members.size();
    }

    @Override
    public boolean isEmpty() {
        return members.isEmpty();
    }

    @Override
    public void clear() {
        if (members.isEmpty()) return;
        members.clear();
        version.incrementAndGet();
    }

    @Override
    public Iterator<E> iterator() {
        Object[] elements = currentSnapshot().elements;
        return new Iterator<>() {
            private int index;
            private E previous;

            @Override
            public boolean hasNext() {
                return index < elements.length;
            }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                previous = (E) elements[index++];
                return previous;
            }

            @Override
            public void remove() {
                if (previous == null) throw new IllegalStateException();
                ConcurrentSnapshotSet.this.remove(previous);
                previous = null;
            }
        };
    }

    @Override
    public Object[] toArray() {
        return currentSnapshot().elements.clone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] target) {
        return Arrays.asList(currentSnapshot().elements).toArray(target);
    }

    private Snapshot currentSnapshot() {
        while (true) {
            long observed = version.get();
            Snapshot current = snapshot;
            if (current.version == observed) return current;
            Object[] elements = members.keySet().toArray();
            if (version.get() != observed) continue;
            Snapshot updated = new Snapshot(observed, elements);
            snapshot = updated;
            return updated;
        }
    }

    private record Snapshot(long version, Object[] elements) { }
}
