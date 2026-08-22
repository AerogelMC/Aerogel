package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.context.ChunkContext;
import dev.aerogel.loader.internal.ChunkContextBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk")
abstract class LevelChunkContextMixin implements ChunkContextBridge {
    @Unique private volatile ChunkContext aerogel$chunkContext;

    @Override
    public ChunkContext aerogel$context() {
        return aerogel$chunkContext;
    }

    @Override
    public void aerogel$context(ChunkContext context) {
        aerogel$chunkContext = context;
    }

}
