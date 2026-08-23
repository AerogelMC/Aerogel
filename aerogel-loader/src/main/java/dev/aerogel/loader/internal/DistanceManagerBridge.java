package dev.aerogel.loader.internal;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.util.TriState;
import net.minecraft.world.level.TicketStorage;

public interface DistanceManagerBridge {
    void aerogel$forEachPublishedEntityTickingChunk(LongConsumer consumer);
    void aerogel$blockTickingListener(LongConsumer listener);
    TriState aerogel$publishedPlayersNearby(long chunkKey);
    long aerogel$spawnDistanceVersion();
    TicketStorage aerogel$ticketStorage();
}
