package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.SimulationChunkTrackerBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.server.level.SimulationChunkTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.server.level.DistanceManager")
abstract class DistanceManagerMixin implements DistanceManagerBridge {
    @Shadow @Final private SimulationChunkTracker simulationChunkTracker;

    @Override
    public void aerogel$forEachPublishedEntityTickingChunk(LongConsumer consumer) {
        ((SimulationChunkTrackerBridge) (Object) simulationChunkTracker)
            .aerogel$forEachEntityTickingChunk(consumer);
    }

    @Override
    public boolean aerogel$isPublishedEntityTickingChunk(long chunkKey) {
        return ((SimulationChunkTrackerBridge) (Object) simulationChunkTracker)
            .aerogel$isEntityTickingChunk(chunkKey);
    }

    @Override
    public void aerogel$blockTickingListener(LongConsumer listener) {
        ((SimulationChunkTrackerBridge) (Object) simulationChunkTracker)
            .aerogel$blockTickingListener(listener);
    }
}
