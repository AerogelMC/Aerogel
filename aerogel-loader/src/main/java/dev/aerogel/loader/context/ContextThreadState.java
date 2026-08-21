package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

final class ContextThreadState {
    private static final ThreadLocal<AccessScope> CURRENT = new ThreadLocal<>();

    private ContextThreadState() {
    }

    static AccessScope current() {
        return CURRENT.get();
    }

    static void enter(AccessScope scope) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested chunk ownership scope");
        }
        CURRENT.set(scope);
    }

    static void leave() {
        CURRENT.remove();
    }

    record AccessScope(ChunkContextImpl primary, LongOpenHashSet ownedKeys) {
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
