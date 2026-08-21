package it.unimi.dsi.fastutil.longs;

import java.util.function.LongConsumer;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface LongSet {
    boolean add(long value);
    boolean remove(long value);
    boolean contains(long value);
    boolean isEmpty();
    int size();
    LongIterator iterator();
    void forEach(LongConsumer action);
}
