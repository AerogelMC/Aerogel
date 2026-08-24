package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.server.ServerTickEndEvent;
import dev.aerogel.api.event.server.ServerTickStartEvent;
import dev.aerogel.loader.command.TpsMonitor;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.jfr.ServerTickTimingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerTickMixin {
    @Unique private ServerTickTimingEvent aerogel$tickTiming;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void aerogel$sampleTps(BooleanSupplier hasTimeLeft, CallbackInfo callbackInfo) {
        if (ServerTickTimingEvent.enabled()) {
            ServerTickTimingEvent timing = new ServerTickTimingEvent();
            timing.serverTick = ((net.minecraft.server.MinecraftServer) (Object) this)
                .getTickCount();
            timing.begin();
            aerogel$tickTiming = timing;
        }
        NativeTickCoordinator.registerMainServer((net.minecraft.server.MinecraftServer) (Object) this);
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
        NativeTickCoordinator.endServerTick();
        NativeTickCoordinator.pumpMainThread();
        ServerTickTimingEvent timing = aerogel$tickTiming;
        aerogel$tickTiming = null;
        if (timing != null) {
            timing.end();
            timing.commit();
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void aerogel$drainContextsBeforeStop(CallbackInfo callbackInfo) {
        NativeTickCoordinator.beginShutdownDrain();
        NativeTickCoordinator.drainForShutdown();
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void aerogel$closeContextsAfterStop(CallbackInfo callbackInfo) {
        // Vanilla still pumps server tasks while saving and closing worlds. Keep
        // Context producers alive until those futures can publish their terminal
        // completion; closing them at HEAD leaves waitForTasks with no producer.
        AerogelRuntime.stopContextDispatch();
        NativeTickCoordinator.finishShutdownDrain();
    }
}
