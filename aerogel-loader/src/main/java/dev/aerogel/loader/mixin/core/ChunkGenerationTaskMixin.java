package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.ChunkStatusTaskGraph;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ChunkGenerationTask")
abstract class ChunkGenerationTaskMixin {
    @Shadow @Final private GeneratingChunkMap chunkMap;
    @Shadow @Final private ChunkPos pos;
    @Shadow private ChunkStatus scheduledStatus;
    @Shadow @Final public ChunkStatus targetStatus;
    @Shadow private volatile boolean markedForCancellation;
    @Shadow @Final private StaticCache2D<GenerationChunkHolder> cache;
    @Shadow private boolean needsGeneration;

    @Shadow protected abstract void scheduleLayer(ChunkStatus status, boolean generation);
    @Shadow protected abstract CompletableFuture<?> waitForScheduledLayer();
    @Shadow protected abstract boolean canLoadWithoutGeneration();
    @Shadow protected abstract void releaseClaim();

    @Unique private ChunkStatusTaskGraph aerogel$statusGraph;
    @Unique private boolean aerogel$claimReleased;

    /**
     * Preserves vanilla's two-phase EMPTY loading decision, then replaces the
     * status-wide barriers with the exact ChunkStep dependency graph.
     */
    @Overwrite
    public CompletableFuture<?> runUntilWait() {
        while (true) {
            if (aerogel$statusGraph != null) {
                if (markedForCancellation) aerogel$statusGraph.cancel();
                CompletableFuture<Void> terminal = aerogel$statusGraph.terminal();
                if (!terminal.isDone()) return terminal;
                if (aerogel$statusGraph.successful()) scheduledStatus = targetStatus;
                aerogel$releaseClaimOnce();
                return null;
            }

            CompletableFuture<?> waiting = waitForScheduledLayer();
            if (waiting != null) return waiting;
            if (markedForCancellation || scheduledStatus == targetStatus) {
                aerogel$releaseClaimOnce();
                return null;
            }

            if (scheduledStatus == null) {
                scheduleLayer(ChunkStatus.EMPTY, false);
                scheduledStatus = ChunkStatus.EMPTY;
                continue;
            }

            if (scheduledStatus != ChunkStatus.EMPTY) {
                throw new IllegalStateException(
                    "ChunkStatus graph started after vanilla advanced past EMPTY");
            }

            if (!needsGeneration && !canLoadWithoutGeneration()) {
                needsGeneration = true;
                scheduleLayer(ChunkStatus.EMPTY, true);
                continue;
            }

            aerogel$statusGraph = new ChunkStatusTaskGraph(
                chunkMap, cache, pos.x(), pos.z(), targetStatus, needsGeneration,
                () -> markedForCancellation = true);
        }
    }

    @Inject(method = "markForCancellation", at = @At("RETURN"))
    private void aerogel$cancelStatusGraph(CallbackInfo callback) {
        ChunkStatusTaskGraph graph = aerogel$statusGraph;
        if (graph != null) graph.cancel();
    }

    @Unique
    private void aerogel$releaseClaimOnce() {
        if (aerogel$claimReleased) return;
        aerogel$claimReleased = true;
        releaseClaim();
    }
}
