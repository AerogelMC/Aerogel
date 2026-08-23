package it.unimi.dsi.fastutil.longs;

import java.util.ArrayList;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class LongArrayList {
    private final ArrayList<Long> values;
    public LongArrayList() { values = new ArrayList<>(); }
    public LongArrayList(int expected) { values = new ArrayList<>(expected); }
    public boolean add(long value) { return values.add(value); }
    public long getLong(int index) { return values.get(index); }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
}
