package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.SimulationChunkTrackerBridge;
import dev.aerogel.loader.internal.NaturalSpawnDistanceBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.SimulationChunkTracker;
import net.minecraft.util.TriState;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.server.level.DistanceManager")
abstract class DistanceManagerMixin implements DistanceManagerBridge {
    @Shadow @Final private SimulationChunkTracker simulationChunkTracker;
    @Shadow @Final private DistanceManager.FixedPlayerDistanceChunkTracker
        naturalSpawnChunkCounter;
    @Shadow @Final private TicketStorage ticketStorage;

    @Override
    public void aerogel$forEachPublishedEntityTickingChunk(LongConsumer consumer) {
        ((SimulationChunkTrackerBridge) (Object) simulationChunkTracker)
            .aerogel$forEachEntityTickingChunk(consumer);
    }

    @Override
    public void aerogel$blockTickingListener(LongConsumer listener) {
        ((SimulationChunkTrackerBridge) (Object) simulationChunkTracker)
            .aerogel$blockTickingListener(listener);
    }

    @Override
    public TriState aerogel$publishedPlayersNearby(long chunkKey) {
        return ((NaturalSpawnDistanceBridge) naturalSpawnChunkCounter)
            .aerogel$publishedPlayersNearby(chunkKey);
    }

    @Override
    public long aerogel$spawnDistanceVersion() {
        return ((NaturalSpawnDistanceBridge) naturalSpawnChunkCounter)
            .aerogel$spawnDistanceVersion();
    }

    @Override
    public TicketStorage aerogel$ticketStorage() {
        return ticketStorage;
    }
}
