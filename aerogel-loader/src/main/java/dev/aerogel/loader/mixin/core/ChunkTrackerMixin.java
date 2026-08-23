package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ExactChunkDistanceGraph;
import dev.aerogel.loader.internal.ExactChunkTrackerBridge;
import dev.aerogel.loader.runtime.AerogelRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;

@Mixin(targets = "net.minecraft.server.level.ChunkTracker")
abstract class ChunkTrackerMixin implements ExactChunkTrackerBridge {
    @Unique private ExactChunkDistanceGraph aerogel$exactDistances;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$createExactDistanceOwners(
        int levelCount, int expectedLevelSize, int expectedTotalSize,
        CallbackInfo callback
    ) {
        aerogel$exactDistances = new ExactChunkDistanceGraph(
            levelCount,
            AerogelRuntime.contextWorkerCount(),
            AerogelRuntime::submitContextComputation,
            AerogelRuntime::invokeContextOwners);
    }

    @Inject(method = "update(JIZ)V", at = @At("HEAD"), cancellable = true)
    private void aerogel$queueExactSourceUpdate(
        long chunkKey, int level, boolean decreasing, CallbackInfo callback
    ) {
        aerogel$updateSource(chunkKey, level);
        callback.cancel();
    }

    @Override
    public void aerogel$updateSource(long chunkKey, int level) {
        aerogel$exactDistances.updateSource(chunkKey, level);
    }

    @Override
    public int aerogel$runExactUpdates(
        int maximumUpdates, ExactChunkDistanceGraph.LevelPublisher publisher
    ) {
        int published = aerogel$exactDistances.publishCompleted(publisher);
        return maximumUpdates - Math.min(maximumUpdates, published);
    }

    @Override
    public CompletableFuture<Void> aerogel$publicationAfterQueuedUpdates() {
        return aerogel$exactDistances.publicationAfterQueuedUpdates();
    }
}
