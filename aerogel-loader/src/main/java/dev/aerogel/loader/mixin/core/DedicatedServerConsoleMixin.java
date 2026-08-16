package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.command.InteractiveConsole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.dedicated.DedicatedServer$1")
abstract class DedicatedServerConsoleMixin {
    @Shadow @Final private DedicatedServer this$0;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void aerogel$interactiveConsole(CallbackInfo callbackInfo) {
        if (InteractiveConsole.run(this$0)) {
            callbackInfo.cancel();
        }
    }
}
