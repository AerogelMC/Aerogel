package dev.aerogel.loader.context;

import java.util.concurrent.ConcurrentHashMap;

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
    private final ConcurrentHashMap<ChunkContextImpl, ChunkContextImpl.TickState> contexts =
        new ConcurrentHashMap<>();

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
        contexts.putIfAbsent(context, state);
    }

    void releaseProducer() {
        int remaining = producers.decrementAndGet();
        if (remaining < 0) {
            throw new IllegalStateException("Native tick producer released twice");
        }
        if (remaining != 0) return;
        contexts.forEach(ChunkContextImpl::closeTickInput);
        contexts.clear();
    }

    void seal() {
        releaseProducer();
    }
}
