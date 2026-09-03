package dev.aerogel.loader.internal;

import net.minecraft.server.level.GenerationChunkHolder;
import dev.aerogel.loader.worldgen.ChunkGenerationCoordinator;

/** Submits a ready world-generation node to vanilla's priority dispatcher. */
public interface GenerationNodeExecutorBridge {
    void aerogel$submitGenerationNode(GenerationChunkHolder holder, Runnable task);
    ChunkGenerationCoordinator aerogel$generationCoordinator();
}
