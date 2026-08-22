package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.context.ChunkContext;
import dev.aerogel.loader.internal.ChunkContextBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import dev.aerogel.loader.context.ConcurrentInt2ObjectMap;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk")
abstract class LevelChunkContextMixin implements ChunkContextBridge {
    @Shadow @Final @Mutable
    private Int2ObjectMap<Object> gameEventListenerRegistrySections;
    @Unique private volatile ChunkContext aerogel$chunkContext;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$concurrentGameEventListenerRegistry(CallbackInfo callback) {
        gameEventListenerRegistrySections = new ConcurrentInt2ObjectMap<>();
    }

    @Override
    public ChunkContext aerogel$context() {
        return aerogel$chunkContext;
    }

    @Override
    public void aerogel$context(ChunkContext context) {
        aerogel$chunkContext = context;
    }

}
