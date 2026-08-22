package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.runtime.AerogelRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces vanilla's fixed four player-loading slots with live CPU headroom. */
@Mixin(targets = "net.minecraft.server.level.ThrottlingChunkTaskDispatcher")
abstract class ThrottlingChunkTaskDispatcherMixin {
    @Shadow @Final @Mutable private int maxChunksInExecution;

    @Inject(method = "popTasks", at = @At("HEAD"))
    private void aerogel$useAvailableContextCapacity(
        CallbackInfoReturnable<Object> callbackInfo
    ) {
        maxChunksInExecution = AerogelRuntime.availableChunkLoadingSlots();
    }
}
