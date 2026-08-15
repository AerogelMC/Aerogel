package dev.aerogel.loader.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The built-in name is a fallback. For competing cancellable HEAD injectors,
// plugin Mixins with the normal priority must execute before this fallback.
@Mixin(targets = "net.minecraft.server.MinecraftServer", priority = 10000)
abstract class MinecraftServerBrandMixin {
    @Inject(method = "getServerModName", at = @At("HEAD"), cancellable = true)
    private void aerogel$serverBrand(CallbackInfoReturnable<String> callbackInfo) {
        callbackInfo.setReturnValue("aerogel");
    }
}
