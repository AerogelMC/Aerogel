package it.unimi.dsi.fastutil.longs;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface LongBidirectionalIterator extends LongIterator {
    boolean hasPrevious();
    long previousLong();
    default Long previous() { return previousLong(); }
}
