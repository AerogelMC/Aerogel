package dev.aerogel.loader.internal;

import dev.aerogel.api.context.ChunkContext;

public interface ChunkContextBridge {
    ChunkContext aerogel$context();

    void aerogel$context(ChunkContext context);
}
