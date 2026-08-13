package dev.aerogel.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void aerogel$beforeServerLoop(CallbackInfo callbackInfo) {
        System.out.println("[Aerogel Example] Mixin reached MinecraftServer.runServer().");
    }
}
