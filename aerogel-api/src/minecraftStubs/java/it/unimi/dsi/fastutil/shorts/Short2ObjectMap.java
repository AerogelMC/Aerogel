package it.unimi.dsi.fastutil.shorts;

import it.unimi.dsi.fastutil.objects.ObjectCollection;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface Short2ObjectMap<V> {
    V get(short key);
    V put(short key, V value);
    V remove(short key);
    ObjectCollection<V> values();
    void clear();
}
