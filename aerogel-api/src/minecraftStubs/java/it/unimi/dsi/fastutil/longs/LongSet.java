package it.unimi.dsi.fastutil.longs;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface LongSet {
    boolean add(long value);
    boolean remove(long value);
    boolean contains(long value);
    boolean isEmpty();
    int size();
    default long[] toLongArray() {
        long[] values = new long[size()];
        LongIterator iterator = iterator();
        int index = 0;
        while (iterator.hasNext()) values[index++] = iterator.nextLong();
        return values;
    }
    LongIterator iterator();
    default void forEach(LongConsumer action) {
        forEach((java.util.function.LongConsumer) action);
    }
    default void forEach(java.util.function.LongConsumer action) {
        throw new UnsupportedOperationException();
    }
}
