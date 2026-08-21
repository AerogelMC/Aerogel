package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.LongConsumer;

/** Snapshot-iterated ordered section index with concurrent exact-key mutation. */
public final class ConcurrentLongSortedSet extends LongAVLTreeSet {
    private final NavigableSet<Long> values;

    public ConcurrentLongSortedSet() {
        values = new ConcurrentSkipListSet<>();
    }

    private ConcurrentLongSortedSet(NavigableSet<Long> values) {
        this.values = values;
    }

    @Override public boolean add(long value) { return values.add(value); }
    @Override public boolean remove(long value) { return values.remove(value); }
    @Override public boolean contains(long value) { return values.contains(value); }
    @Override public boolean isEmpty() { return values.isEmpty(); }
    @Override public int size() { return values.size(); }
    @Override public void forEach(LongConsumer action) { values.forEach(action::accept); }

    @Override
    public LongSortedSet subSet(long from, long to) {
        return new ConcurrentLongSortedSet(values.subSet(from, true, to, false));
    }

    @Override
    public LongBidirectionalIterator iterator() {
        return iteratorOf(values.iterator());
    }

    private static LongBidirectionalIterator iteratorOf(Iterator<Long> source) {
        return new LongBidirectionalIterator() {
            private long[] visited = new long[0];
            private int visitedSize;
            private int index;
            @Override public boolean hasNext() {
                return index < visitedSize || source.hasNext();
            }
            @Override public long nextLong() {
                if (index < visitedSize) return visited[index++];
                long value = source.next();
                if (visitedSize == visited.length) {
                    visited = Arrays.copyOf(visited, Math.max(1, visitedSize * 2));
                }
                visited[visitedSize++] = value;
                index++;
                return value;
            }
            @Override public Long next() { return nextLong(); }
            @Override public boolean hasPrevious() { return index > 0; }
            @Override public long previousLong() { return visited[--index]; }
        };
    }
}
