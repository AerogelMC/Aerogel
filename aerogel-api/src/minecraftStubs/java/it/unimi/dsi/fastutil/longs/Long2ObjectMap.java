package it.unimi.dsi.fastutil.longs;

import it.unimi.dsi.fastutil.objects.ObjectSet;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface Long2ObjectMap<V> extends Long2ObjectFunction<V> {
    interface Entry<V> {
        long getLongKey();
        V getValue();
    }

    V get(long key);
    V put(long key, V value);
    V remove(long key);
    V computeIfAbsent(long key, Long2ObjectFunction<? extends V> mappingFunction);
    LongSet keySet();
    ObjectSet<Entry<V>> long2ObjectEntrySet();
    void defaultReturnValue(V value);
    int size();
}
