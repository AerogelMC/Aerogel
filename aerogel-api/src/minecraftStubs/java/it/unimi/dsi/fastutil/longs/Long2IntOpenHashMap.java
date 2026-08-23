package it.unimi.dsi.fastutil.longs;

import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class Long2IntOpenHashMap implements Long2IntMap {
    private final Map<Long, Integer> values;
    private int defaultValue;
    public Long2IntOpenHashMap() { values = new HashMap<>(); }
    public Long2IntOpenHashMap(int expected) { values = new HashMap<>(expected); }
    public int get(long key) { return values.getOrDefault(key, defaultValue); }
    public int put(long key, int value) {
        Integer previous = values.put(key, value);
        return previous == null ? defaultValue : previous;
    }
    public int remove(long key) {
        Integer previous = values.remove(key);
        return previous == null ? defaultValue : previous;
    }
    public boolean containsKey(long key) { return values.containsKey(key); }
    public boolean isEmpty() { return values.isEmpty(); }
    public int size() { return values.size(); }
    public void defaultReturnValue(int value) { defaultValue = value; }
    @Override public ObjectSet<Long2IntMap.Entry> long2IntEntrySet() {
        return new EntrySet();
    }
    private final class EntrySet extends AbstractSet<Long2IntMap.Entry>
        implements ObjectSet<Long2IntMap.Entry> {
        @Override public int size() { return values.size(); }
        @Override public Iterator<Long2IntMap.Entry> iterator() {
            Iterator<Map.Entry<Long, Integer>> source = values.entrySet().iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return source.hasNext(); }
                @Override public Long2IntMap.Entry next() {
                    Map.Entry<Long, Integer> entry = source.next();
                    return new Long2IntMap.Entry() {
                        @Override public long getLongKey() { return entry.getKey(); }
                        @Override public int getIntValue() { return entry.getValue(); }
                    };
                }
            };
        }
    }
}
