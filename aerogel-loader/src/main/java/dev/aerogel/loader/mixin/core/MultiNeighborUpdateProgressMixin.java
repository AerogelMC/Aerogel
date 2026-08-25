package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NeighborUpdatesProgressBridge;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Mirrors MultiNeighborUpdate.runNext's exact vanilla completion predicate. */
@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater$MultiNeighborUpdate")
abstract class MultiNeighborUpdateProgressMixin implements NeighborUpdatesProgressBridge {
    @Shadow private int idx;

    @Override
    public boolean aerogel$hasRemainingNeighborUpdates() {
        return idx < NeighborUpdater.UPDATE_ORDER.length;
    }
}
