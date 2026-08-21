package it.unimi.dsi.fastutil.ints;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class Int2ObjectLinkedOpenHashMap<V> implements Int2ObjectMap<V> {
    private final Map<Integer, V> values = new LinkedHashMap<>();
    @Override public V get(int key) { return values.get(key); }
    @Override public V put(int key, V value) { return values.put(key, value); }
    @Override public V remove(int key) { return values.remove(key); }
    @Override public boolean containsKey(int key) { return values.containsKey(key); }
    @Override public ObjectCollection<V> values() {
        return new ObjectArrayList<>(values.values());
    }
    @Override public int size() { return values.size(); }
    @Override public void clear() { values.clear(); }
}
