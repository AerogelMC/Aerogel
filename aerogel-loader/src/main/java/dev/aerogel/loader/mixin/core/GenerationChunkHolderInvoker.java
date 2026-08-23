package dev.aerogel.loader.mixin.core;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.GenerationChunkHolder")
public interface GenerationChunkHolderInvoker {
    @Invoker("applyStep")
    CompletableFuture<ChunkResult<ChunkAccess>> aerogel$applyStep(
        ChunkStep step,
        GeneratingChunkMap chunkMap,
        StaticCache2D<GenerationChunkHolder> cache
    );
}
