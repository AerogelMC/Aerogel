package it.unimi.dsi.fastutil.longs;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface Long2ObjectMap<V> extends Long2ObjectFunction<V> {
    V get(long key);
    V put(long key, V value);
    V remove(long key);
    V computeIfAbsent(long key, Long2ObjectFunction<? extends V> mappingFunction);
    LongSet keySet();
    void defaultReturnValue(V value);
    int size();
}
