package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.server.ServerTickEndEvent;
import dev.aerogel.api.event.server.ServerTickStartEvent;
import dev.aerogel.loader.command.TpsMonitor;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.context.NativeTickCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerTickMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void aerogel$sampleTps(BooleanSupplier hasTimeLeft, CallbackInfo callbackInfo) {
        NativeTickCoordinator.beginServerTick();
        NativeTickCoordinator.pumpMainThread();
        TpsMonitor.tick(System.nanoTime());
        AerogelRuntime.tick(this);
        if (EventHooks.hasListeners(ServerTickStartEvent.class)) {
            EventHooks.post(new ServerTickStartEvent(EventHooks.cast(this)));
        }
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void aerogel$finishTick(BooleanSupplier hasTimeLeft, CallbackInfo callbackInfo) {
        if (EventHooks.hasListeners(ServerTickEndEvent.class)) {
            EventHooks.post(new ServerTickEndEvent(EventHooks.cast(this)));
        }
        NativeTickCoordinator.pumpMainThread();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void aerogel$drainContextsBeforeStop(CallbackInfo callbackInfo) {
        NativeTickCoordinator.drainForShutdown();
        AerogelRuntime.stopContextDispatch();
    }
}
