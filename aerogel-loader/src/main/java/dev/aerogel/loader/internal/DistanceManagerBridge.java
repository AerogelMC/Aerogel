package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.util.TriState;
import net.minecraft.world.level.TicketStorage;
import java.util.concurrent.CompletableFuture;

public interface DistanceManagerBridge {
    void aerogel$forEachPublishedEntityTickingChunk(LongConsumer consumer);
    void aerogel$blockTickingListener(LongConsumer listener);
    TriState aerogel$publishedPlayersNearby(long chunkKey);
    long aerogel$spawnDistanceVersion();
    void aerogel$spawnDistanceListener(LongConsumer listener);
    TicketStorage aerogel$ticketStorage();
    CompletableFuture<Void> aerogel$loadingDistancePublication();
}
