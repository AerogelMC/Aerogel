package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NeighborUpdaterLimitBridge;
import dev.aerogel.loader.context.NeighborUpdaterContinuationBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayDeque;
import java.util.List;

@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater")
public interface CollectingNeighborUpdaterAccessor
    extends NeighborUpdaterLimitBridge, NeighborUpdaterContinuationBridge {
    @Override
    @Accessor("maxChainedNeighborUpdates")
    int aerogel$maximumChainedUpdates();

    @Override
    @Invoker("runUpdates")
    void aerogel$resumeNeighborUpdates();

    @Override
    @Accessor("stack")
    ArrayDeque<?> aerogel$neighborUpdateStack();

    @Override
    @Accessor("addedThisLayer")
    List<?> aerogel$neighborUpdatesAddedThisLayer();

    @Override
    @Accessor("count")
    int aerogel$neighborUpdateCount();

    @Override
    @Accessor("count")
    void aerogel$neighborUpdateCount(int count);
}
