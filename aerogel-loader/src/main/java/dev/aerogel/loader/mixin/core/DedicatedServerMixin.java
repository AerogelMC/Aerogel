package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.server.ServerStartedEvent;
import dev.aerogel.api.event.server.ServerStartingEvent;
import dev.aerogel.api.event.server.ServerStoppedEvent;
import dev.aerogel.api.event.server.ServerStoppingEvent;
import dev.aerogel.loader.command.PluginsCommand;
import dev.aerogel.loader.command.RestartCommand;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.loader.restart.RestartCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.dedicated.DedicatedServer")
abstract class DedicatedServerMixin {
    @Inject(method = "initServer", at = @At("HEAD"))
    private void aerogel$starting(CallbackInfoReturnable<Boolean> callbackInfo) {
        EventHooks.post(new ServerStartingEvent(EventHooks.cast(this)));
    }

    @Inject(method = "initServer", at = @At("RETURN"))
    private void aerogel$registerCommands(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (callbackInfo.getReturnValueZ()) {
            AerogelRuntime.attachServer(this);
            PluginsCommand.register(this);
            RestartCommand.register(this);
            EventHooks.post(new ServerStartedEvent(EventHooks.cast(this)));
            RestartCoordinator.serverReady();
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void aerogel$stopping(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callbackInfo) {
        RestartCoordinator.serverStopping();
        EventHooks.post(new ServerStoppingEvent(EventHooks.cast(this)));
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void aerogel$stopped(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callbackInfo) {
        EventHooks.post(new ServerStoppedEvent(EventHooks.cast(this)));
        AerogelRuntime.pluginManager().shutdown();
        RestartCoordinator.serverStopped();
    }
}
