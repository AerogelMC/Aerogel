package dev.aerogel.loader.context;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Preserves vanilla's cross-tick natural-spawn ordering without blocking the
 * server thread. Chunks inside one wave remain independent Context work; only
 * the next wave's entity-count snapshot waits for this wave to finish.
 */
public final class NaturalSpawnWave {
    private static final int CLOSED = -1;

    private final WorldContextImpl owner;
    private final CompletableFuture<Void> activationBarrier;
    private final CompletableFuture<Void> completion;
    private final NativeTickToken tickToken;
    /* One hold for state preparation and one for synchronous task registration. */
    private final PaddedAtomicInteger pending = new PaddedAtomicInteger(2);
    private final PaddedAtomicBoolean sealed = new PaddedAtomicBoolean();
    private final PaddedAtomicBoolean preparationFinished = new PaddedAtomicBoolean();
    private final PaddedAtomicBoolean tickInputReleased = new PaddedAtomicBoolean();
    private final AtomicReference<Lifecycle> lifecycle;
    private final AtomicReference<Runnable> startAction = new AtomicReference<>();
    private final AtomicReference<Runnable> cancellationAction = new AtomicReference<>();

    NaturalSpawnWave(
        WorldContextImpl owner, CompletableFuture<Void> completion,
        NativeTickToken tickToken
    ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.activationBarrier = CompletableFuture.completedFuture(null);
        this.completion = Objects.requireNonNull(completion, "completion");
        this.tickToken = tickToken;
        this.lifecycle = new AtomicReference<>(Lifecycle.PENDING);
        retainTickInput();
        completion.whenComplete((ignored, failure) -> owner.naturalSpawnWaveComplete(this));
    }

    NaturalSpawnWave(
        CompletableFuture<Void> predecessor, CompletableFuture<Void> completion,
        NativeTickToken tickToken
    ) {
        this.owner = null;
        this.activationBarrier = Objects.requireNonNull(predecessor, "predecessor");
        this.completion = Objects.requireNonNull(completion, "completion");
        this.tickToken = tickToken;
        this.lifecycle = new AtomicReference<>(Lifecycle.ACTIVE);
        retainTickInput();
    }

    NaturalSpawnWave(
        CompletableFuture<Void> predecessor, CompletableFuture<Void> completion
    ) {
        this(predecessor, completion, null);
    }

    private void retainTickInput() {
        if (tickToken != null && !tickToken.retainProducer()) {
            throw new IllegalStateException("Native tick closed before spawn wave");
        }
    }

    void whenActive(Runnable action, Runnable cancelled) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(cancelled, "cancelled");
        if (!startAction.compareAndSet(null, action)
            || !cancellationAction.compareAndSet(null, cancelled)) {
            throw new IllegalStateException("Natural-spawn wave start registered twice");
        }
        runLifecycleAction();
    }

    void afterPredecessor(Runnable action) {
        whenActive(action, () -> { });
    }

    void activate() {
        if (lifecycle.compareAndSet(Lifecycle.PENDING, Lifecycle.ACTIVE)) {
            runLifecycleAction();
        }
    }

    void cancel() {
        Lifecycle current = lifecycle.get();
        while (current != Lifecycle.CANCELLED && current != Lifecycle.CLOSED) {
            if (lifecycle.compareAndSet(current, Lifecycle.CANCELLED)) {
                pending.set(CLOSED);
                runLifecycleAction();
                releaseTickInput();
                completion.complete(null);
                return;
            }
            current = lifecycle.get();
        }
    }

    private void runLifecycleAction() {
        Lifecycle current = lifecycle.get();
        if (current == Lifecycle.ACTIVE) {
            Runnable action = startAction.getAndSet(null);
            if (action != null) {
                activationBarrier.whenComplete((ignored, failure) -> action.run());
            }
        } else if (current == Lifecycle.CANCELLED) {
            Runnable action = cancellationAction.getAndSet(null);
            if (action != null) action.run();
            startAction.set(null);
        }
    }

    CompletableFuture<Void> completion() {
        return completion;
    }

    NativeTickToken tickToken() {
        return tickToken;
    }

    /** Registers asynchronous work before its parent registration is released. */
    public boolean register() {
        int current = pending.get();
        while (current != CLOSED) {
            if (pending.compareAndSet(current, current + 1)) return true;
            current = pending.get();
        }
        return false;
    }

    public void taskComplete() {
        release();
    }

    void preparationComplete() {
        release();
        preparationFinished.set(true);
        releaseTickInputWhenRegistered();
    }

    public void seal() {
        if (sealed.compareAndSet(false, true)) {
            release();
            releaseTickInputWhenRegistered();
        }
    }

    private void releaseTickInputWhenRegistered() {
        if (tickToken != null && sealed.get() && preparationFinished.get()
            && tickInputReleased.compareAndSet(false, true)) {
            tickToken.releaseProducer();
        }
    }

    private void releaseTickInput() {
        if (tickToken != null && tickInputReleased.compareAndSet(false, true)) {
            tickToken.releaseProducer();
        }
    }

    private void release() {
        int current = pending.get();
        while (current > 0) {
            int updated = current - 1;
            if (!pending.compareAndSet(current, updated)) {
                current = pending.get();
                continue;
            }
            if (updated == 0 && pending.compareAndSet(0, CLOSED)) {
                lifecycle.set(Lifecycle.CLOSED);
                completion.complete(null);
            }
            return;
        }
    }

    private enum Lifecycle {
        PENDING,
        ACTIVE,
        CANCELLED,
        CLOSED
    }
}
