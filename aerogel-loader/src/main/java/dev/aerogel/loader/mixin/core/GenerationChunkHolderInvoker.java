package dev.aerogel.loader.mixin.core;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.GenerationChunkHolder")
public interface GenerationChunkHolderInvoker {
    @Invoker("getTicketLevel")
    int aerogel$getTicketLevel();

    @Invoker("scheduleChunkGenerationTask")
    CompletableFuture<ChunkResult<ChunkAccess>> aerogel$scheduleChunkGenerationTask(
        ChunkStatus targetStatus,
        ChunkMap chunkMap
    );

    @Invoker("applyStep")
    CompletableFuture<ChunkResult<ChunkAccess>> aerogel$applyStep(
        ChunkStep step,
        GeneratingChunkMap chunkMap,
        StaticCache2D<GenerationChunkHolder> cache
    );

    @Invoker("updateHighestAllowedStatus")
    void aerogel$updateHighestAllowedStatus(ChunkMap chunkMap);
}
