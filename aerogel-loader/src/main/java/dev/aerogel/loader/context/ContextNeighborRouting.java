package dev.aerogel.loader.context;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

/** Selects the neighbor-update queue owned by the currently executing chunk context. */
public final class ContextNeighborRouting {
    private ContextNeighborRouting() {
    }

    public static CollectingNeighborUpdater current(
        Level level, CollectingNeighborUpdater fallback
    ) {
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || scope.primary().world().level() != level) return fallback;
        int maximumChainedUpdates =
            ((NeighborUpdaterLimitBridge) (Object) fallback)
                .aerogel$maximumChainedUpdates();
        return scope.primary().neighborUpdater(level, maximumChainedUpdates);
    }
}
