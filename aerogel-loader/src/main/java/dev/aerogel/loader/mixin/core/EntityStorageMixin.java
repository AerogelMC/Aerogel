package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes the empty-entity-chunk cache across parallel owner save lanes. */
@Mixin(targets = "net.minecraft.world.level.chunk.storage.EntityStorage")
abstract class EntityStorageMixin {
    @Shadow @Final @Mutable private LongSet emptyChunks;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$concurrentEmptyChunkIndex(CallbackInfo callback) {
        emptyChunks = new ConcurrentLongSet();
    }
}
