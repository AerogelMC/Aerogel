package dev.aerogel.loader.context;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Lock-free multi-producer ingress with an unbounded, lossless single-consumer drain. */
public final class ConcurrentIngress<T> {
    private final ConcurrentLinkedQueue<T> pending = new ConcurrentLinkedQueue<>();

    public void offer(T value) {
        pending.add(Objects.requireNonNull(value, "value"));
    }

    public void drain(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        T value;
        while ((value = pending.poll()) != null) consumer.accept(value);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }
}
