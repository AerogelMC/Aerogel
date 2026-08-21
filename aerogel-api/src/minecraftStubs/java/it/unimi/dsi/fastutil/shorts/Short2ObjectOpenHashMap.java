package it.unimi.dsi.fastutil.shorts;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class Short2ObjectOpenHashMap<V> implements Short2ObjectMap<V> {
    private final Map<Short, V> values = new HashMap<>();
    @Override public V get(short key) { return values.get(key); }
    @Override public V put(short key, V value) { return values.put(key, value); }
    @Override public V remove(short key) { return values.remove(key); }
    @Override public ObjectCollection<V> values() {
        return new ObjectArrayList<>(values.values());
    }
    @Override public void clear() { values.clear(); }
}
