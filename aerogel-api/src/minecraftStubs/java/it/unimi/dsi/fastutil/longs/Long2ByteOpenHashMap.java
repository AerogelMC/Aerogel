package it.unimi.dsi.fastutil.longs;

import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class Long2ByteOpenHashMap implements Long2ByteMap {
    private final Map<Long, Byte> values = new HashMap<>();
    private byte defaultValue;
    public byte get(long key) { return values.getOrDefault(key, defaultValue); }
    public byte put(long key, byte value) {
        Byte previous = values.put(key, value);
        return previous == null ? defaultValue : previous;
    }
    public byte remove(long key) {
        Byte previous = values.remove(key);
        return previous == null ? defaultValue : previous;
    }
    public boolean containsKey(long key) { return values.containsKey(key); }
    public void defaultReturnValue(byte value) { defaultValue = value; }
    @Override public ObjectSet<Long2ByteMap.Entry> long2ByteEntrySet() {
        return new EntrySet();
    }
    private final class EntrySet extends AbstractSet<Long2ByteMap.Entry>
        implements ObjectSet<Long2ByteMap.Entry> {
        @Override public int size() { return values.size(); }
        @Override public Iterator<Long2ByteMap.Entry> iterator() {
            Iterator<Map.Entry<Long, Byte>> source = values.entrySet().iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return source.hasNext(); }
                @Override public Long2ByteMap.Entry next() {
                    Map.Entry<Long, Byte> entry = source.next();
                    return new Long2ByteMap.Entry() {
                        @Override public long getLongKey() { return entry.getKey(); }
                        @Override public byte getByteValue() { return entry.getValue(); }
                    };
                }
            };
        }
    }
}
