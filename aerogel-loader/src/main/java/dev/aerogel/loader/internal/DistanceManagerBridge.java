package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.util.TriState;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import dev.aerogel.loader.context.ExactChunkDistanceGraph;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface DistanceManagerBridge {
    void aerogel$forEachPublishedEntityTickingChunk(LongConsumer consumer);
    void aerogel$blockTickingListener(LongConsumer listener);
    TriState aerogel$publishedPlayersNearby(long chunkKey);
    long aerogel$spawnDistanceVersion();
    void aerogel$spawnDistanceListener(LongConsumer listener);
    TicketStorage aerogel$ticketStorage();
    Executor aerogel$mainThreadExecutor();
    CompletableFuture<Void> aerogel$loadingDistancePublication();
    void aerogel$bindLoadingGenerationPublisher(
        ExactChunkDistanceGraph.GenerationPublisher publisher);
    List<ChunkHolder> aerogel$applyLoadingGeneration(
        ExactChunkDistanceGraph.ChangeBatch changes, ChunkMap chunkMap);
    void aerogel$updateHighestAllowedStatus(ChunkHolder holder, ChunkMap chunkMap);
    CompletableFuture<Void> aerogel$updateHolderFutures(
        ChunkHolder holder, ChunkMap chunkMap);
}
