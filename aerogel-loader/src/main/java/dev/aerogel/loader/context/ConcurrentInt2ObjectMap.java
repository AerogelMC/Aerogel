package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent primitive-key publication map for the visible entity index. */
public final class ConcurrentInt2ObjectMap<V> extends Int2ObjectLinkedOpenHashMap<V> {
    private final ConcurrentHashMap<Integer, V> values = new ConcurrentHashMap<>();

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
