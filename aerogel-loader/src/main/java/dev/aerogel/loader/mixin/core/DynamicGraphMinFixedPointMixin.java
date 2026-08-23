package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ExactChunkTrackerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint")
abstract class DynamicGraphMinFixedPointMixin {
    @Shadow protected abstract void setLevel(long chunkKey, int level);

    @Inject(method = "runUpdates(I)I", at = @At("HEAD"), cancellable = true)
    private void aerogel$runOwnedExactChunkUpdates(
        int maximumUpdates, CallbackInfoReturnable<Integer> callback
    ) {
        if ((Object) this instanceof ExactChunkTrackerBridge exact) {
            callback.setReturnValue(exact.aerogel$runExactUpdates(
                maximumUpdates, this::setLevel));
        }
    }
}
