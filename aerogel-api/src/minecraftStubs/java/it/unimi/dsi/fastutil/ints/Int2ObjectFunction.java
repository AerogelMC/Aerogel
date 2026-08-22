package it.unimi.dsi.fastutil.ints;

@FunctionalInterface
public interface Int2ObjectFunction<V> {
    V get(int key);
}
