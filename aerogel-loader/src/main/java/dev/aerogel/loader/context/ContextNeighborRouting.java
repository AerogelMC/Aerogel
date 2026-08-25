package dev.aerogel.loader.context;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;

/** Selects the neighbor-update queue owned by the currently executing chunk context. */
public final class ContextNeighborRouting {
    private ContextNeighborRouting() {
    }

    public static CollectingNeighborUpdater current(
        Level level, CollectingNeighborUpdater fallback, BlockPos position
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return fallback;
        ContextThreadState.AccessScope scope = ContextThreadState.current();
        if (scope == null || scope.primary().world().level() != level) return fallback;
        CollectingNeighborUpdater causal = NeighborUpdateContinuation.current();
        if (causal != null) return causal;
        long targetKey = WorldContextImpl.key(position.getX() >> 4, position.getZ() >> 4);
        int maximumChainedUpdates =
            ((NeighborUpdaterLimitBridge) (Object) fallback)
                .aerogel$maximumChainedUpdates();
        // neighborShapeChanged and the multi-neighbor methods enqueue work before
        // their exact target is executed.  A target outside the current ownership
        // set therefore remains in the causal source queue; the executeUpdate /
        // executeShapeUpdate redirects perform the exact Context hand-off later.
        // When the target is already jointly owned, keep its queue stable by using
        // that target Context rather than whichever Context happens to be primary.
        ChunkContextImpl queueOwner = scope.containsKey(targetKey)
            ? scope.primary().world().context(
                position.getX() >> 4, position.getZ() >> 4)
            : scope.primary();
        return queueOwner.neighborUpdater(level, maximumChainedUpdates);
    }
}
