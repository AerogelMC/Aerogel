package it.unimi.dsi.fastutil.longs;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class Long2ObjectOpenHashMap<V> implements Long2ObjectMap<V>, Cloneable {
    private final Map<Long, V> values;
    private V defaultValue;
    public Long2ObjectOpenHashMap() { values = new HashMap<>(); }
    public Long2ObjectOpenHashMap(int expected) { values = new HashMap<>(expected); }
    public V get(long key) { return values.getOrDefault(key, defaultValue); }
    public V getOrDefault(long key, V fallback) { return values.getOrDefault(key, fallback); }
    public boolean containsKey(long key) { return values.containsKey(key); }
    public V put(long key, V value) { return values.put(key, value); }
    public V remove(long key) { return values.remove(key); }
    public V computeIfAbsent(long key, Long2ObjectFunction<? extends V> mappingFunction) {
        return values.computeIfAbsent(key, ignored -> mappingFunction.get(key));
    }
    public LongSet keySet() {
        LongOpenHashSet result = new LongOpenHashSet();
        values.keySet().forEach(result::add);
        return result;
    }
    public void defaultReturnValue(V value) { defaultValue = value; }
    public V defaultReturnValue() { return defaultValue; }
    public ObjectCollection<V> values() { return new ObjectArrayList<>(values.values()); }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
    public void clear() { values.clear(); }
    @Override public Long2ObjectOpenHashMap<V> clone() {
        Long2ObjectOpenHashMap<V> copy = new Long2ObjectOpenHashMap<>(values.size());
        copy.values.putAll(values);
        copy.defaultValue = defaultValue;
        return copy;
    }
}
