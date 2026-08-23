package dev.aerogel.loader.mixin.core;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(targets = "net.minecraft.server.level.ChunkHolder")
abstract class ChunkHolderMixin {
    @Shadow public abstract CompletableFuture<ChunkResult<LevelChunk>>
        getTickingChunkFuture();

    @Unique private volatile CompletableFuture<?> aerogel$resolvedTickingFuture;
    @Unique private volatile LevelChunk aerogel$resolvedTickingChunk;

    /**
     * A holder's completed ticking future is immutable and is replaced when
     * its full status changes. Resolve each future identity once instead of
     * running CompletableFuture.getNow()/ChunkResult.orElse for every natural
     * spawn candidate on every tick.
     */
    @Inject(method = "getTickingChunk", at = @At("HEAD"), cancellable = true)
    private void aerogel$readResolvedTickingChunk(
        CallbackInfoReturnable<LevelChunk> callback
    ) {
        CompletableFuture<ChunkResult<LevelChunk>> future =
            getTickingChunkFuture();
        if (future == aerogel$resolvedTickingFuture) {
            callback.setReturnValue(aerogel$resolvedTickingChunk);
            return;
        }
        if (!future.isDone()) return;

        LevelChunk chunk = future.getNow(null).orElse(null);
        // Bind the result only if status transition code has not replaced the
        // future meanwhile. Returning the captured result matches vanilla's
        // single field read even in that race.
        if (getTickingChunkFuture() == future) {
            aerogel$resolvedTickingChunk = chunk;
            aerogel$resolvedTickingFuture = future;
        }
        callback.setReturnValue(chunk);
    }
}
