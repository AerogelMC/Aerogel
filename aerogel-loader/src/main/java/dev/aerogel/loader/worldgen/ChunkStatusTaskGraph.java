package dev.aerogel.loader.worldgen;

import dev.aerogel.loader.internal.GenerationNodeExecutorBridge;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** One request facade over the world-owned shared generation DAG. */
public final class ChunkStatusTaskGraph {
    private final Runnable cancellationAction;
    private final CompletableFuture<Void> terminal = new CompletableFuture<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final ChunkGenerationCoordinator coordinator;
    private volatile boolean successful;

    public ChunkStatusTaskGraph(
        GeneratingChunkMap chunkMap,
        StaticCache2D<GenerationChunkHolder> cache,
        int centerX,
        int centerZ,
        ChunkStatus targetStatus,
        boolean generationExpected,
        Runnable cancellationAction
    ) {
        if (!(chunkMap instanceof GenerationNodeExecutorBridge bridge)) {
            throw new IllegalStateException(
                "GeneratingChunkMap does not expose the world generation coordinator");
        }
        this.cancellationAction = cancellationAction;
        this.coordinator = bridge.aerogel$generationCoordinator();
        coordinator.request(cache, centerX, centerZ, targetStatus,
            generationExpected).whenComplete((ignored, failure) -> {
                if (!finished.compareAndSet(false, true)) return;
                successful = failure == null;
                if (failure != null) cancellationAction.run();
                terminal.complete(null);
            });
    }

    public CompletableFuture<Void> terminal() {
        return terminal;
    }

    public boolean successful() {
        return successful;
    }

    public void cancel() {
        if (!finished.compareAndSet(false, true)) return;
        cancellationAction.run();
        terminal.complete(null);
    }

    public int nodeCount() {
        return coordinator.inFlightCount();
    }
}
