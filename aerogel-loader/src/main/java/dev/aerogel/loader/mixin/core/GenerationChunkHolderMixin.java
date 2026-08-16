package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.world.ChunkPreLoadEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.ChunkMapTrackingBridge;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.level.GenerationChunkHolder")
abstract class GenerationChunkHolderMixin {
    @Shadow public abstract ChunkAccess getLatestChunk();
    @Shadow public abstract ChunkPos getPos();
    @Shadow @Final public static java.util.concurrent.CompletableFuture<ChunkResult<ChunkAccess>>
        UNLOADED_CHUNK_FUTURE;
    @Unique
    private boolean aerogel$loadStarted;

    @Inject(
        method = "scheduleChunkGenerationTask(Lnet/minecraft/world/level/chunk/status/ChunkStatus;"
            + "Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/GenerationChunkHolder;"
                + "getOrCreateFuture(Lnet/minecraft/world/level/chunk/status/ChunkStatus;)"
                + "Ljava/util/concurrent/CompletableFuture;",
            shift = At.Shift.BEFORE
        ),
        cancellable = true
    )
    private void aerogel$preLoad(
        ChunkStatus requestedStatus, ChunkMap chunkMap,
        CallbackInfoReturnable<java.util.concurrent.CompletableFuture<ChunkResult<ChunkAccess>>> callbackInfo
    ) {
        if (!EventHooks.hasListeners(ChunkPreLoadEvent.class)) return;
        if (aerogel$loadStarted || getLatestChunk() != null) return;

        ChunkPreLoadEvent event = new ChunkPreLoadEvent(
            ((ChunkMapTrackingBridge) chunkMap).aerogel$level(), getPos(), requestedStatus);
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(UNLOADED_CHUNK_FUTURE);
            return;
        }
        aerogel$loadStarted = true;
    }
}
