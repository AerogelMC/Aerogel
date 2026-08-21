package it.unimi.dsi.fastutil.longs;

import java.util.ArrayList;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.LongConsumer;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class LongAVLTreeSet implements LongSortedSet {
    private final NavigableSet<Long> values = new TreeSet<>();
    @Override public boolean add(long value) { return values.add(value); }
    @Override public boolean remove(long value) { return values.remove(value); }
    @Override public boolean contains(long value) { return values.contains(value); }
    @Override public boolean isEmpty() { return values.isEmpty(); }
    @Override public int size() { return values.size(); }
    @Override public void forEach(LongConsumer action) { values.forEach(action::accept); }
    @Override public LongSortedSet subSet(long from, long to) {
        LongAVLTreeSet result = new LongAVLTreeSet();
        values.subSet(from, true, to, false).forEach(result::add);
        return result;
    }
    @Override public LongBidirectionalIterator iterator() {
        java.util.List<Long> snapshot = new ArrayList<>(values);
        return new LongBidirectionalIterator() {
            private int index;
            @Override public boolean hasNext() { return index < snapshot.size(); }
            @Override public long nextLong() { return snapshot.get(index++); }
            @Override public Long next() { return nextLong(); }
            @Override public boolean hasPrevious() { return index > 0; }
            @Override public long previousLong() { return snapshot.get(--index); }
        };
    }
}
