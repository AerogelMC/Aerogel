package it.unimi.dsi.fastutil.ints;

public interface Int2ObjectMap<V> {
    V get(int key);
    V put(int key, V value);
    V remove(int key);
    boolean containsKey(int key);
    V computeIfAbsent(int key, java.util.function.IntFunction<? extends V> function);
    V computeIfAbsent(int key, Int2ObjectFunction<? extends V> function);
    it.unimi.dsi.fastutil.objects.ObjectCollection<V> values();
    int size();
    void clear();
}
