package dev.aerogel.loader.context;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** Routes Level's shared random source to the exact owner while parallel work runs. */
public final class ContextRandomRouting {
    private ContextRandomRouting() {
    }

    public static RandomSource current(Level level) {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || scope.primary().world().level() != level) return null;
        return scope.primary().random();
    }
}
