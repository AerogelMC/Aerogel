package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.DistanceManagerBridge;
import dev.aerogel.loader.internal.SimulationChunkTrackerBridge;
import dev.aerogel.loader.internal.NaturalSpawnDistanceBridge;
import dev.aerogel.loader.internal.ExactChunkTrackerBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.SimulationChunkTracker;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.util.TriState;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.objectweb.asm.Opcodes;
import java.util.concurrent.CompletableFuture;

@Mixin(targets = "net.minecraft.server.level.DistanceManager")
abstract class DistanceManagerMixin implements DistanceManagerBridge {
    @Shadow @Final private SimulationChunkTracker simulationChunkTracker;
    @Shadow @Final private LoadingChunkTracker loadingChunkTracker;
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

    @Override
    public CompletableFuture<Void> aerogel$loadingDistancePublication() {
        return ((ExactChunkTrackerBridge) (Object) loadingChunkTracker)
            .aerogel$publicationAfterQueuedUpdates();
    }

    @Inject(
        method = "runAllUpdates",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/server/level/DistanceManager;"
                + "chunksToUpdateFutures:Ljava/util/Set;",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        ),
        cancellable = true
    )
    private void aerogel$deferLoadingDependentTail(
        ChunkMap chunkMap, CallbackInfoReturnable<Boolean> callback
    ) {
        if (!aerogel$loadingDistancePublication().isDone()) {
            callback.setReturnValue(false);
        }
    }
}
