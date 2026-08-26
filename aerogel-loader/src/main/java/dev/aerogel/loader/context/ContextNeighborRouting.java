package dev.aerogel.loader.context;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

/** Selects the neighbor-update queue owned by the current causal transaction. */
public final class ContextNeighborRouting {
    private ContextNeighborRouting() {
    }

    public static CollectingNeighborUpdater current(
        Level level, CollectingNeighborUpdater fallback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return fallback;
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || scope.primary().world().level() != level) return fallback;
        CollectingNeighborUpdater causal = NeighborUpdateContinuation.current();
        if (causal != null) return causal;
        int maximumChainedUpdates =
            ((NeighborUpdaterLimitBridge) (Object) fallback)
                .aerogel$maximumChainedUpdates();
        /*
         * A causal ownership group has exactly one vanilla queue. If a callback
         * crosses a Context while count is nonzero, later updates join that same
         * addedThisLayer instead of forking a second queue for the same circuit.
         * Disjoint groups still have independent queues and execute in parallel.
         */
        return NeighborCausalExecution.currentUpdater(level, maximumChainedUpdates);
    }
}
