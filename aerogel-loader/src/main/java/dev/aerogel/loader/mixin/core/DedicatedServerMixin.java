package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.server.ServerStartedEvent;
import dev.aerogel.api.event.server.ServerStartingEvent;
import dev.aerogel.api.event.server.ServerStoppedEvent;
import dev.aerogel.api.event.server.ServerStoppingEvent;
import dev.aerogel.loader.command.PluginsCommand;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.dedicated.DedicatedServer")
abstract class DedicatedServerMixin {
    @Inject(method = "initServer", at = @At("HEAD"))
    private void aerogel$starting(CallbackInfoReturnable<Boolean> callbackInfo) {
        EventHooks.post(new ServerStartingEvent(this));
    }

    @Inject(method = "initServer", at = @At("RETURN"))
    private void aerogel$registerCommands(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (callbackInfo.getReturnValueZ()) {
            PluginsCommand.register(this);
            EventHooks.post(new ServerStartedEvent(this));
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void aerogel$stopping(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callbackInfo) {
        EventHooks.post(new ServerStoppingEvent(this));
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void aerogel$stopped(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callbackInfo) {
        EventHooks.post(new ServerStoppedEvent(this));
    }
}
