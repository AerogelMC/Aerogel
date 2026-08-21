package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NeighborUpdaterLimitBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater")
public interface CollectingNeighborUpdaterAccessor extends NeighborUpdaterLimitBridge {
    @Override
    @Accessor("maxChainedNeighborUpdates")
    int aerogel$maximumChainedUpdates();
}
