package dev.aerogel.loader.internal;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A lock-free copy-on-write map whose readers always observe one immutable generation.
 *
 * <p>This is intended for maps with frequent reads and very rare structural changes. Iterators
 * retain the generation that existed when they were created, so codecs and other long-running
 * readers cannot be invalidated by a concurrent writer.</p>
 */
public final class ConcurrentSnapshotMap<K, V> extends AbstractMap<K, V>
    implements ConcurrentMap<K, V> {
    private final AtomicReference<Map<K, V>> generation;

    public ConcurrentSnapshotMap(Map<? extends K, ? extends V> initial) {
        generation = new AtomicReference<>(Map.copyOf(initial));
    }

    @Override
    public int size() {
        return generation.get().size();
    }

    @Override
    public boolean isEmpty() {
        return generation.get().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return generation.get().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return generation.get().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return generation.get().get(key);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return generation.get().entrySet();
    }

    @Override
    public Set<K> keySet() {
        return generation.get().keySet();
    }

    @Override
    public Collection<V> values() {
        return generation.get().values();
    }

    @Override
    public V put(K key, V value) {
        requireEntry(key, value);
        for (;;) {
            Map<K, V> current = generation.get();
            V previous = current.get(key);
            Map<K, V> next = copyWith(current, key, value);
            if (generation.compareAndSet(current, next)) return previous;
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        requireEntry(key, value);
        for (;;) {
            Map<K, V> current = generation.get();
            V previous = current.get(key);
            if (previous != null) return previous;
            Map<K, V> next = copyWith(current, key, value);
            if (generation.compareAndSet(current, next)) return null;
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        V candidate = null;
        for (;;) {
            Map<K, V> current = generation.get();
            V present = current.get(key);
            if (present != null) return present;
            if (candidate == null) {
                candidate = mappingFunction.apply(key);
                if (candidate == null) return null;
            }
            Map<K, V> next = copyWith(current, key, candidate);
            if (generation.compareAndSet(current, next)) return candidate;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> values) {
        if (values.isEmpty()) return;
        Map<K, V> checked = new HashMap<>(values.size());
        values.forEach((key, value) -> {
            requireEntry(key, value);
            checked.put(key, value);
        });
        for (;;) {
            Map<K, V> current = generation.get();
            HashMap<K, V> next = new HashMap<>(current);
            next.putAll(checked);
            if (generation.compareAndSet(current, Map.copyOf(next))) return;
        }
    }

    @Override
    public V remove(Object key) {
        for (;;) {
            Map<K, V> current = generation.get();
            V previous = current.get(key);
            if (previous == null) return null;
            HashMap<K, V> next = new HashMap<>(current);
            next.remove(key);
            if (generation.compareAndSet(current, Map.copyOf(next))) return previous;
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        for (;;) {
            Map<K, V> current = generation.get();
            if (!Objects.equals(current.get(key), value) || !current.containsKey(key)) return false;
            HashMap<K, V> next = new HashMap<>(current);
            next.remove(key);
            if (generation.compareAndSet(current, Map.copyOf(next))) return true;
        }
    }

    @Override
    public V replace(K key, V value) {
        requireEntry(key, value);
        for (;;) {
            Map<K, V> current = generation.get();
            V previous = current.get(key);
            if (previous == null) return null;
            Map<K, V> next = copyWith(current, key, value);
            if (generation.compareAndSet(current, next)) return previous;
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        requireEntry(key, newValue);
        for (;;) {
            Map<K, V> current = generation.get();
            if (!Objects.equals(current.get(key), oldValue) || !current.containsKey(key)) return false;
            Map<K, V> next = copyWith(current, key, newValue);
            if (generation.compareAndSet(current, next)) return true;
        }
    }

    @Override
    public V computeIfPresent(
        K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        for (;;) {
            Map<K, V> current = generation.get();
            V previous = current.get(key);
            if (previous == null) return null;
            V value = remappingFunction.apply(key, previous);
            Map<K, V> next = value == null
                ? copyWithout(current, key)
                : copyWith(current, key, value);
            if (generation.compareAndSet(current, next)) return value;
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        for (;;) {
            Map<K, V> current = generation.get();
            V value = remappingFunction.apply(key, current.get(key));
            Map<K, V> next = value == null
                ? copyWithout(current, key)
                : copyWith(current, key, value);
            if (generation.compareAndSet(current, next)) return value;
        }
    }

    @Override
    public V merge(
        K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction
    ) {
        requireEntry(key, value);
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        for (;;) {
            Map<K, V> current = generation.get();
            V previous = current.get(key);
            V merged = previous == null ? value : remappingFunction.apply(previous, value);
            Map<K, V> next = merged == null
                ? copyWithout(current, key)
                : copyWith(current, key, merged);
            if (generation.compareAndSet(current, next)) return merged;
        }
    }

    @Override
    public void clear() {
        for (;;) {
            Map<K, V> current = generation.get();
            if (current.isEmpty() || generation.compareAndSet(current, Map.of())) return;
        }
    }

    private static <K, V> Map<K, V> copyWith(Map<K, V> current, K key, V value) {
        HashMap<K, V> next = new HashMap<>(current);
        next.put(key, value);
        return Map.copyOf(next);
    }

    private static <K, V> Map<K, V> copyWithout(Map<K, V> current, Object key) {
        if (!current.containsKey(key)) return current;
        HashMap<K, V> next = new HashMap<>(current);
        next.remove(key);
        return Map.copyOf(next);
    }

    private static void requireEntry(Object key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }
}
