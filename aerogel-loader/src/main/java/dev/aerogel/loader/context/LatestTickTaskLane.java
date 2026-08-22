package dev.aerogel.loader.context;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one producer pass at a time and retains only the newest pass that has not
 * started. This bounds work before it fans out across thousands of Contexts.
 */
final class LatestTickTaskLane implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Aerogel-Contexts");

    private final ContextServiceImpl scheduler;
    private final PaddedAtomicReference<Window> window =
        new PaddedAtomicReference<>(Window.EMPTY);
    private final PaddedLongAccumulator settledTick =
        new PaddedLongAccumulator(Long::max, 0L);
    private volatile boolean closed;

    LatestTickTaskLane(ContextServiceImpl scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    void offer(NativeTickToken token, Runnable action) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(action, "action");
        if (closed || !token.retainProducer()) return;
        Request created = new Request(this, token, action);
        while (!closed) {
            Window observed = window.get();
            Request active = observed.active;
            Request pending = observed.pending;
            long requestedTick = token.serverTick();
            long newest = settledTick.get();
            if (active != null) newest = Math.max(newest, active.token.serverTick());
            if (pending != null) newest = Math.max(newest, pending.token.serverTick());
            if (requestedTick <= newest) {
                created.cancel();
                return;
            }
            Window updated = active == null
                ? new Window(created, null) : new Window(active, created);
            if (!window.compareAndSet(observed, updated)) continue;
            if (pending != null) pending.cancel();
            if (active == null) created.start();
            return;
        }
        created.cancel();
    }

    private void complete(Request request) {
        while (true) {
            Window observed = window.get();
            if (observed.active != request) return;
            Request next = closed ? null : observed.pending;
            Window updated = next == null ? Window.EMPTY : new Window(next, null);
            if (!window.compareAndSet(observed, updated)) continue;
            settledTick.accumulate(request.token.serverTick());
            if (closed && observed.pending != null) observed.pending.cancel();
            if (next != null) next.start();
            return;
        }
    }

    @Override
    public void close() {
        closed = true;
        while (true) {
            Window observed = window.get();
            Window updated = observed.active == null
                ? Window.EMPTY : new Window(observed.active, null);
            if (!window.compareAndSet(observed, updated)) continue;
            if (observed.pending != null) observed.pending.cancel();
            return;
        }
    }

    private static final class Request {
        private final LatestTickTaskLane lane;
        private final NativeTickToken token;
        private final Runnable action;
        private final AtomicReference<Lifecycle> lifecycle =
            new AtomicReference<>(Lifecycle.PENDING);

        private Request(
            LatestTickTaskLane lane, NativeTickToken token, Runnable action
        ) {
            this.lane = lane;
            this.token = token;
            this.action = action;
        }

        private void start() {
            if (!lifecycle.compareAndSet(Lifecycle.PENDING, Lifecycle.ACTIVE)) return;
            if (!lane.scheduler.dispatch(() -> {
                try {
                    if (!lane.closed) action.run();
                } catch (Throwable error) {
                    LOGGER.log(Level.SEVERE, "Tick producer pass failed", error);
                } finally {
                    lifecycle.set(Lifecycle.CLOSED);
                    token.releaseProducer();
                    lane.complete(this);
                }
            })) {
                lifecycle.set(Lifecycle.CLOSED);
                token.releaseProducer();
                lane.complete(this);
            }
        }

        private void cancel() {
            if (lifecycle.compareAndSet(Lifecycle.PENDING, Lifecycle.CANCELLED)) {
                token.releaseProducer();
            }
        }
    }

    private record Window(Request active, Request pending) {
        private static final Window EMPTY = new Window(null, null);
    }

    private enum Lifecycle {
        PENDING,
        ACTIVE,
        CANCELLED,
        CLOSED
    }
}
