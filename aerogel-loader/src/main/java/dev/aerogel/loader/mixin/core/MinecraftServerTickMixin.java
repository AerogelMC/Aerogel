package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.server.ServerTickEndEvent;
import dev.aerogel.api.event.server.ServerTickStartEvent;
import dev.aerogel.loader.command.TpsMonitor;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerTickMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void aerogel$sampleTps(BooleanSupplier hasTimeLeft, CallbackInfo callbackInfo) {
        TpsMonitor.tick(System.nanoTime());
        EventHooks.post(new ServerTickStartEvent(this));
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void aerogel$finishTick(BooleanSupplier hasTimeLeft, CallbackInfo callbackInfo) {
        EventHooks.post(new ServerTickEndEvent(this));
    }
}
