package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Primitive-key facade used where vanilla only calls get/put on its section map. */
public final class ConcurrentLong2ObjectMap<V> extends Long2ObjectOpenHashMap<V> {
    private final ConcurrentHashMap<Long, V> values = new ConcurrentHashMap<>();
    private volatile V defaultValue;

    @Override public V get(long key) { return values.getOrDefault(spread(key), defaultValue); }
    @Override public V put(long key, V value) { return values.put(spread(key), value); }
    @Override public V remove(long key) { return values.remove(spread(key)); }
    @Override public V computeIfAbsent(
        long key, Long2ObjectFunction<? extends V> mappingFunction
    ) {
        return values.computeIfAbsent(spread(key), ignored -> mappingFunction.get(key));
    }
    @Override public LongSet keySet() {
        LongOpenHashSet snapshot = new LongOpenHashSet();
        values.keySet().forEach(mixed -> snapshot.add(unspread(mixed.longValue())));
        return snapshot;
    }
    @Override public void defaultReturnValue(V value) { defaultValue = value; }
    @Override public ObjectCollection<V> values() {
        return new ObjectArrayList<>(values.values());
    }
    @Override public int size() { return values.size(); }
    @Override public void clear() { values.clear(); }

    /**
     * SplitMix64's bijective finalizer prevents packed (x,z) keys from collapsing into
     * ConcurrentHashMap tree bins through Long.hashCode's upper/lower XOR.
     */
    static long spread(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static long unspread(long value) {
        value = (value ^ (value >>> 31) ^ (value >>> 62)) * 0x319642b2d24d8ec3L;
        value = (value ^ (value >>> 27) ^ (value >>> 54)) * 0x96de1b173f119089L;
        return value ^ (value >>> 30) ^ (value >>> 60);
    }
}
