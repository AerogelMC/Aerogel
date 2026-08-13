package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.restart.RestartCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.Connection")
abstract class ConnectionMixin {
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$freezeRestartView(
        @Coerce Object packet,
        @Coerce Object listener,
        boolean flush,
        CallbackInfo callbackInfo
    ) {
        if (RestartCoordinator.suppressOutgoing(this, packet)) {
            callbackInfo.cancel();
        }
    }
}
