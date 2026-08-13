package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.runtime.AerogelRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.Main")
abstract class MainBootstrapMixin {
    @Inject(
        method = "main",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/Bootstrap;validate()V",
            shift = At.Shift.AFTER
        )
    )
    private static void aerogel$loadPluginsAfterBootstrap(String[] arguments, CallbackInfo callbackInfo) {
        AerogelRuntime.loadPluginsAfterBootstrap();
    }
}
