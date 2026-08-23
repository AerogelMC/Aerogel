package it.unimi.dsi.fastutil.bytes;

import java.util.ArrayList;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class ByteArrayList {
    private final ArrayList<Byte> values;
    public ByteArrayList() { values = new ArrayList<>(); }
    public ByteArrayList(int expected) { values = new ArrayList<>(expected); }
    public boolean add(byte value) { return values.add(value); }
    public byte getByte(int index) { return values.get(index); }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
}
