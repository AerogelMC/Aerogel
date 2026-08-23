package dev.aerogel.loader.context;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Stable worker-local storage that does not touch ThreadLocalMap on Aerogel Context workers.
 * The fallback preserves utility and test behaviour on foreign threads.
 */
public final class ContextWorkerLocal<T> {
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();

    private final int slot = NEXT_SLOT.getAndIncrement();
    private final Supplier<? extends T> initial;
    private final ThreadLocal<T> fallback;

    private ContextWorkerLocal(Supplier<? extends T> initial) {
        this.initial = initial;
        fallback = initial == null ? new ThreadLocal<>() : ThreadLocal.withInitial(initial);
    }

    public static <T> ContextWorkerLocal<T> create() {
        return new ContextWorkerLocal<>(null);
    }

    public static <T> ContextWorkerLocal<T> withInitial(Supplier<? extends T> initial) {
        return new ContextWorkerLocal<>(Objects.requireNonNull(initial, "initial"));
    }

    @SuppressWarnings("unchecked")
    public T get() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof ContextWorkerThread worker)) return fallback.get();
        Object[] values = worker.localValues;
        if (slot >= values.length) return initialize(worker);
        Object value = values[slot];
        return value != null ? (T) value : initialize(worker);
    }

    public void set(T value) {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof ContextWorkerThread worker)) {
            fallback.set(value);
            return;
        }
        ensureCapacity(worker)[slot] = value;
    }

    public void remove() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof ContextWorkerThread worker)) {
            fallback.remove();
            return;
        }
        if (slot < worker.localValues.length) worker.localValues[slot] = null;
    }

    private T initialize(ContextWorkerThread worker) {
        if (initial == null) return null;
        T value = initial.get();
        ensureCapacity(worker)[slot] = value;
        return value;
    }

    private Object[] ensureCapacity(ContextWorkerThread worker) {
        Object[] values = worker.localValues;
        if (slot < values.length) return values;
        int length = values.length;
        while (length <= slot) length <<= 1;
        return worker.localValues = Arrays.copyOf(values, length);
    }
}
