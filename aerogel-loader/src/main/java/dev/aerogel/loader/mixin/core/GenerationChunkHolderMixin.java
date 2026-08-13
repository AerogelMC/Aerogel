package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.world.ChunkPreLoadEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.level.GenerationChunkHolder")
abstract class GenerationChunkHolderMixin {
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
        @Coerce Object requestedStatus, @Coerce Object chunkMap,
        CallbackInfoReturnable<Object> callbackInfo
    ) {
        if (aerogel$loadStarted || EventHooks.call(this, "getLatestChunk") != null) return;

        ChunkPreLoadEvent event = new ChunkPreLoadEvent(
            EventHooks.cast(EventHooks.field(chunkMap, "level")),
            EventHooks.cast(EventHooks.call(this, "getPos")),
            EventHooks.cast(requestedStatus));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.setReturnValue(EventHooks.staticField(
                this, "net.minecraft.server.level.GenerationChunkHolder", "UNLOADED_CHUNK_FUTURE"));
            return;
        }
        aerogel$loadStarted = true;
    }
}
