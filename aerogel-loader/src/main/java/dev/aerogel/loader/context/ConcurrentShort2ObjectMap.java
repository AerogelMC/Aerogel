package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Primitive-key facade publishing a POI section's records without a shared lock. */
public final class ConcurrentShort2ObjectMap<V> extends Short2ObjectOpenHashMap<V> {
    private final ConcurrentHashMap<Short, V> values = new ConcurrentHashMap<>();

    @Override public V get(short key) { return values.get(key); }
    @Override public V put(short key, V value) { return values.put(key, value); }
    @Override public V remove(short key) { return values.remove(key); }
    @Override public ObjectCollection<V> values() {
        return new ObjectArrayList<>(values.values());
    }
    @Override public void clear() { values.clear(); }
}
