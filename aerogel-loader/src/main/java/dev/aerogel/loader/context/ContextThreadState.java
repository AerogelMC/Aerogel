package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

final class ContextThreadState {
    /* Only non-context workers (principally deterministic scheduler tests) use this path. */
    private static final ThreadLocal<AccessScope> FALLBACK = new ThreadLocal<>();

    private ContextThreadState() {
    }

    static AccessScope current() {
        Thread thread = Thread.currentThread();
        return thread instanceof ContextWorkerThread worker
            ? worker.accessScope
            : FALLBACK.get();
    }

    static void enter(AccessScope scope) {
        Thread thread = Thread.currentThread();
        if (thread instanceof ContextWorkerThread worker) {
            if (worker.accessScope != null) {
                throw new IllegalStateException("Nested chunk ownership scope");
            }
            worker.accessScope = scope;
        } else {
            if (FALLBACK.get() != null) {
                throw new IllegalStateException("Nested chunk ownership scope");
            }
            FALLBACK.set(scope);
        }
    }

    static void leave() {
        Thread thread = Thread.currentThread();
        if (thread instanceof ContextWorkerThread worker) {
            worker.accessScope = null;
        } else {
            FALLBACK.remove();
        }
    }

    record AccessScope(
        ChunkContextImpl primary,
        LongOpenHashSet ownedKeys,
        boolean interactive
    ) {
        boolean contains(ChunkContextImpl context) {
            return context.world() == primary.world()
                && (context == primary
                    || ownedKeys != null && ownedKeys.contains(context.key()));
        }

        boolean containsKey(long key) {
            return primary.key() == key || ownedKeys != null && ownedKeys.contains(key);
        }
    }
}
