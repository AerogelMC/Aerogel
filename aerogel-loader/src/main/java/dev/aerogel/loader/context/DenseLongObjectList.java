package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Main-owner dense values with constant-time keyed replacement and removal.
 *
 * <p>Removal swaps the last entry into the vacated slot. Natural-spawn candidate
 * order has no semantic meaning, so this avoids copying the complete candidate
 * generation when one chunk enters or leaves the eligible set.</p>
 */
public final class DenseLongObjectList<V> {
    private final Long2IntOpenHashMap indexes = new Long2IntOpenHashMap();
    private final ArrayList<Entry<V>> entries = new ArrayList<>();

    public DenseLongObjectList() {
        indexes.defaultReturnValue(-1);
    }

    public void put(long key, V value) {
        Objects.requireNonNull(value, "value");
        int index = indexes.get(key);
        if (index >= 0) {
            entries.set(index, new Entry<>(key, value));
            return;
        }
        indexes.put(key, entries.size());
        entries.add(new Entry<>(key, value));
    }

    public boolean remove(long key) {
        int index = indexes.remove(key);
        if (index < 0) return false;
        int lastIndex = entries.size() - 1;
        Entry<V> last = entries.remove(lastIndex);
        if (index != lastIndex) {
            entries.set(index, last);
            indexes.put(last.key, index);
        }
        return true;
    }

    public boolean containsKey(long key) {
        return indexes.containsKey(key);
    }

    public int size() {
        return entries.size();
    }

    public void forEach(Consumer<? super V> action) {
        Objects.requireNonNull(action, "action");
        for (int index = 0; index < entries.size(); index++) {
            action.accept(entries.get(index).value);
        }
    }

    private record Entry<V>(long key, V value) { }
}
