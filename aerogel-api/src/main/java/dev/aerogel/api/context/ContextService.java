package dev.aerogel.api.context;

import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/** Server-wide access to chunk ownership contexts. */
public interface ContextService {
    WorldContext world(ServerLevel world);

    Optional<ChunkContext> currentChunk();

    boolean inContext();

    void assertContextThread();

    int workerCount();
}
