package it.unimi.dsi.fastutil.ints;

import java.util.ArrayList;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class IntArrayList {
    private final ArrayList<Integer> values;
    public IntArrayList() { values = new ArrayList<>(); }
    public IntArrayList(int expected) { values = new ArrayList<>(expected); }
    public boolean add(int value) { return values.add(value); }
    public int getInt(int index) { return values.get(index); }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
}
