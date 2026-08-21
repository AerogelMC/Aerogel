package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.Iterator;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.LongConsumer;

/** Snapshot-iterated ordered section index with concurrent exact-key mutation. */
public final class ConcurrentLongSortedSet extends LongAVLTreeSet {
    private final ConcurrentSkipListSet<Long> values = new ConcurrentSkipListSet<>();

    @Override public boolean add(long value) { return values.add(value); }
    @Override public boolean remove(long value) { return values.remove(value); }
    @Override public boolean contains(long value) { return values.contains(value); }
    @Override public boolean isEmpty() { return values.isEmpty(); }
    @Override public int size() { return values.size(); }
    @Override public void forEach(LongConsumer action) { values.forEach(action::accept); }

    @Override
    public LongSortedSet subSet(long from, long to) {
        return snapshot(values.subSet(from, true, to, false));
    }

    @Override
    public LongBidirectionalIterator iterator() {
        return iteratorOf(snapshotValues(values));
    }

    private static LongAVLTreeSet snapshot(NavigableSet<Long> source) {
        LongAVLTreeSet copy = new LongAVLTreeSet();
        source.forEach(copy::add);
        return copy;
    }

    private static java.util.List<Long> snapshotValues(NavigableSet<Long> source) {
        return java.util.List.copyOf(source);
    }

    private static LongBidirectionalIterator iteratorOf(java.util.List<Long> source) {
        return new LongBidirectionalIterator() {
            private int index;
            @Override public boolean hasNext() { return index < source.size(); }
            @Override public long nextLong() { return source.get(index++); }
            @Override public Long next() { return nextLong(); }
            @Override public boolean hasPrevious() { return index > 0; }
            @Override public long previousLong() { return source.get(--index); }
        };
    }
}
