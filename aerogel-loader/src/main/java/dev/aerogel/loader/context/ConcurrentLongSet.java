package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent primitive-facing set for low-frequency cross-owner world indexes. */
public final class ConcurrentLongSet implements LongSet {
    private final Set<Long> values = ConcurrentHashMap.newKeySet();

    @Override
    public boolean add(long value) {
        return values.add(value);
    }

    @Override
    public boolean contains(long value) {
        return values.contains(value);
    }

    @Override
    public boolean remove(long value) {
        return values.remove(value);
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

    @Override
    public LongIterator iterator() {
        Iterator<Long> iterator = values.iterator();
        return new LongIterator() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public long nextLong() { return iterator.next(); }
            @Override public void remove() { iterator.remove(); }
        };
    }
}
