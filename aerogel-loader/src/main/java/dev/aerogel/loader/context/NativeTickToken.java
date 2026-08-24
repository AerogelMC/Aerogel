package dev.aerogel.loader.context;

/**
 * Lifetime of one server-produced tick request.
 *
 * Producers retain the token before moving tick enumeration off the server thread.
 * A Context that admits this token therefore knows exactly when no later phase for
 * the same tick can still arrive, without scanning every loaded Context.
 */
final class NativeTickToken {
    private final long serverTick;
    private final PaddedAtomicInteger producers = new PaddedAtomicInteger(1);
    private final PaddedAtomicReference<ContextServiceImpl> scheduler =
        new PaddedAtomicReference<>();
    /**
     * Intrusive MPSC registration stack. TickState already has exactly this
     * lifetime, so no hash nodes, boxed keys, table resizing, or removal pass is
     * needed. The producer reference acquired by register() prevents the zero
     * transition from draining the head while a publisher is linking its state.
     */
    private final PaddedAtomicReference<ChunkContextImpl.TickState> contexts =
        new PaddedAtomicReference<>();

    NativeTickToken(long serverTick) {
        this.serverTick = serverTick;
    }

    long serverTick() {
        return serverTick;
    }

    boolean retainProducer() {
        int current = producers.get();
        while (current > 0) {
            if (producers.compareAndSet(current, current + 1)) return true;
            current = producers.get();
        }
        return false;
    }

    void register(
        ChunkContextImpl context, ChunkContextImpl.TickState state
    ) {
        // A routed task may discover a new owner Context after the producer pass
        // that created this token has returned. Hold a producer reference across
        // publication so the zero transition cannot sweep the registry between
        // the put and this registration becoming visible. If zero already won,
        // close this state directly instead of publishing an orphaned input.
        if (!retainProducer()) {
            context.closeTickInput(state);
            return;
        }
        ContextServiceImpl contextScheduler = context.scheduler();
        ContextServiceImpl selected = scheduler.get();
        if (selected == null) {
            scheduler.compareAndSet(null, contextScheduler);
            selected = scheduler.get();
        }
        if (selected != contextScheduler) {
            releaseProducer();
            throw new IllegalArgumentException(
                "One native tick token cannot span Context schedulers");
        }
        ChunkContextImpl.TickState observed;
        do {
            observed = contexts.get();
            state.registeredNext(observed);
        } while (!contexts.compareAndSet(observed, state));
        releaseProducer();
    }

    void releaseProducer() {
        int remaining = producers.decrementAndGet();
        if (remaining < 0) {
            throw new IllegalStateException("Native tick producer released twice");
        }
        if (remaining != 0) return;
        ContextServiceImpl owner = scheduler.get();
        if (owner != null && owner.executeComputation(this::closeInputs)) return;
        closeInputs();
    }

    private void closeInputs() {
        ChunkContextImpl.TickState state = contexts.getAndSet(null);
        while (state != null) {
            ChunkContextImpl.TickState next = state.registeredNext();
            state.owner().closeTickInput(state);
            state = next;
        }
    }

    void seal() {
        releaseProducer();
    }
}
