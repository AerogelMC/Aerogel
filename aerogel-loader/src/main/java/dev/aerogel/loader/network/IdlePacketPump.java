package dev.aerogel.loader.network;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Moves already-queued packets through the server thread while it is waiting for the next tick.
 * A run handles only the queue snapshot observed at its start, preserving both queue order and
 * the next tick's opportunity to run under sustained input.
 */
public final class IdlePacketPump {
    private static final BooleanSupplier NEVER_IDLE = () -> false;

    private final Queue<Object> queue;
    private final Consumer<Object> handler;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    private volatile BooleanSupplier idle = NEVER_IDLE;
    private volatile Consumer<Runnable> executor;

    public IdlePacketPump(Queue<Object> queue, Consumer<Object> handler) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public void configure(BooleanSupplier idle, Consumer<Runnable> executor) {
        this.idle = Objects.requireNonNull(idle, "idle");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void request() {
        Consumer<Runnable> currentExecutor = executor;
        if (currentExecutor == null || queue.isEmpty() || !PacketQueueMetrics.idlePumpEnabled()
            || !idle.getAsBoolean()) return;
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            currentExecutor.accept(this::drainSnapshot);
        } catch (RuntimeException exception) {
            scheduled.set(false);
            throw exception;
        }
    }

    public void close() {
        idle = NEVER_IDLE;
        executor = null;
    }

    private void drainSnapshot() {
        try {
            if (!PacketQueueMetrics.idlePumpEnabled() || !idle.getAsBoolean()) return;
            int remaining = queue.size();
            while (remaining-- > 0 && PacketQueueMetrics.idlePumpEnabled()
                && idle.getAsBoolean()) {
                Object entry = queue.poll();
                if (entry == null) break;
                handler.accept(entry);
            }
        } finally {
            scheduled.set(false);
            if (!queue.isEmpty() && PacketQueueMetrics.idlePumpEnabled()
                && idle.getAsBoolean()) request();
        }
    }
}
