package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.server.ServerSaveEndEvent;
import dev.aerogel.api.event.server.ServerSaveStartEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerSaveMixin {
    @Inject(method = "saveAllChunks(ZZZ)Z", at = @At("HEAD"))
    private void aerogel$saveStarting(
        boolean suppressLog, boolean flush, boolean force,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EventHooks.post(new ServerSaveStartEvent(this, suppressLog, flush, force));
    }

    @Inject(method = "saveAllChunks(ZZZ)Z", at = @At("RETURN"))
    private void aerogel$saveComplete(
        boolean suppressLog, boolean flush, boolean force,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        EventHooks.post(new ServerSaveEndEvent(
            this, suppressLog, flush, force, callbackInfo.getReturnValueZ()));
    }
}
