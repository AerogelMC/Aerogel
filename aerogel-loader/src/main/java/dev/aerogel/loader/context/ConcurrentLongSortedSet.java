package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.LongConsumer;

/**
 * Copy-on-write primitive sorted set for section indexes.
 *
 * <p>Entity section membership changes far less often than spatial queries.
 * Publishing a sorted primitive array makes contains, range iteration and
 * forward/backward traversal allocation-free apart from the iterator object;
 * no boxed skip-list nodes or incrementally grown visited arrays are needed.</p>
 */
public final class ConcurrentLongSortedSet extends LongAVLTreeSet {
    private final State state;
    private final boolean hasLower;
    private final long lower;
    private final boolean hasUpper;
    private final long upper;

    public ConcurrentLongSortedSet() {
        this(new State(), false, 0L, false, 0L);
    }

    private ConcurrentLongSortedSet(
        State state, boolean hasLower, long lower, boolean hasUpper, long upper
    ) {
        this.state = state;
        this.hasLower = hasLower;
        this.lower = lower;
        this.hasUpper = hasUpper;
        this.upper = upper;
    }

    @Override
    public boolean add(long value) {
        if (!inRange(value)) {
            throw new IllegalArgumentException("Value outside sorted-set view");
        }
        return state.add(value);
    }

    @Override
    public boolean remove(long value) {
        return inRange(value) && state.remove(value);
    }

    @Override
    public boolean contains(long value) {
        return inRange(value) && Arrays.binarySearch(state.values, value) >= 0;
    }

    @Override
    public boolean isEmpty() {
        long[] snapshot = state.values;
        return lowerIndex(snapshot) == upperIndex(snapshot);
    }

    @Override
    public int size() {
        long[] snapshot = state.values;
        return upperIndex(snapshot) - lowerIndex(snapshot);
    }

    @Override
    public void forEach(LongConsumer action) {
        long[] snapshot = state.values;
        int from = lowerIndex(snapshot);
        int to = upperIndex(snapshot);
        for (int index = from; index < to; index++) {
            action.accept(snapshot[index]);
        }
    }

    @Override
    public LongSortedSet subSet(long from, long to) {
        if (from > to || !withinViewBoundary(from, true)
            || !withinViewBoundary(to, false)) {
            throw new IllegalArgumentException("Range outside sorted-set view");
        }
        return new ConcurrentLongSortedSet(state, true, from, true, to);
    }

    @Override
    public LongBidirectionalIterator iterator() {
        long[] snapshot = state.values;
        int from = lowerIndex(snapshot);
        int to = upperIndex(snapshot);
        return new SnapshotIterator(snapshot, from, to);
    }

    private boolean inRange(long value) {
        return (!hasLower || value >= lower) && (!hasUpper || value < upper);
    }

    private boolean withinViewBoundary(long value, boolean lowerBoundary) {
        if (hasLower && value < lower) return false;
        if (!hasUpper) return true;
        return lowerBoundary ? value < upper : value <= upper;
    }

    private int lowerIndex(long[] snapshot) {
        return hasLower ? insertionPoint(snapshot, lower) : 0;
    }

    private int upperIndex(long[] snapshot) {
        return hasUpper ? insertionPoint(snapshot, upper) : snapshot.length;
    }

    private static int insertionPoint(long[] values, long value) {
        int index = Arrays.binarySearch(values, value);
        return index >= 0 ? index : -index - 1;
    }

    private static final class State {
        private volatile long[] values = new long[0];

        private synchronized boolean add(long value) {
            long[] current = values;
            int index = Arrays.binarySearch(current, value);
            if (index >= 0) return false;
            int insertion = -index - 1;
            long[] next = new long[current.length + 1];
            System.arraycopy(current, 0, next, 0, insertion);
            next[insertion] = value;
            System.arraycopy(current, insertion, next, insertion + 1,
                current.length - insertion);
            values = next;
            return true;
        }

        private synchronized boolean remove(long value) {
            long[] current = values;
            int index = Arrays.binarySearch(current, value);
            if (index < 0) return false;
            long[] next = new long[current.length - 1];
            System.arraycopy(current, 0, next, 0, index);
            System.arraycopy(current, index + 1, next, index,
                current.length - index - 1);
            values = next;
            return true;
        }
    }

    private static final class SnapshotIterator implements LongBidirectionalIterator {
        private final long[] values;
        private final int start;
        private final int end;
        private int index;

        private SnapshotIterator(long[] values, int start, int end) {
            this.values = values;
            this.start = start;
            this.index = start;
            this.end = end;
        }

        @Override public boolean hasNext() { return index < end; }

        @Override
        public long nextLong() {
            if (!hasNext()) throw new NoSuchElementException();
            return values[index++];
        }

        @Override public Long next() { return nextLong(); }
        @Override public boolean hasPrevious() { return index > start; }

        @Override
        public long previousLong() {
            if (!hasPrevious()) throw new NoSuchElementException();
            return values[--index];
        }
    }
}
