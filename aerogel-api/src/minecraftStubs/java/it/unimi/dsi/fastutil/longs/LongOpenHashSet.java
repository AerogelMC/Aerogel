package it.unimi.dsi.fastutil.longs;

import java.util.HashSet;

/** Compile-only subset used by the context ownership implementation. */
public class LongOpenHashSet implements LongSet {
    private final HashSet<Long> delegate = new HashSet<>();

    public LongOpenHashSet() { }

    public LongOpenHashSet(long[] values) {
        for (long value : values) delegate.add(value);
    }

    public boolean add(long value) { return delegate.add(value); }
    public boolean remove(long value) { return delegate.remove(value); }
    public boolean contains(long value) { return delegate.contains(value); }
    public boolean isEmpty() { return delegate.isEmpty(); }
    public int size() { return delegate.size(); }
    public void forEach(java.util.function.LongConsumer action) {
        delegate.forEach(action::accept);
    }
    public LongIterator iterator() {
        java.util.Iterator<Long> iterator = delegate.iterator();
        return new LongIterator() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public long nextLong() { return iterator.next(); }
            @Override public Long next() { return nextLong(); }
        };
    }

    public long[] toLongArray() {
        long[] values = new long[delegate.size()];
        int index = 0;
        for (long value : delegate) values[index++] = value;
        return values;
    }
}
