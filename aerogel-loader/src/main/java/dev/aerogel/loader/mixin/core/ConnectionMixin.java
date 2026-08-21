package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.restart.RestartCoordinator;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

@Mixin(targets = "net.minecraft.network.Connection")
abstract class ConnectionMixin {
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/TickablePacketListener;tick()V"
        )
    )
    private void aerogel$freezeRestartListenerTick(TickablePacketListener listener) {
        if (!RestartCoordinator.suppressListenerTick(this)) {
            if (listener instanceof ServerGamePacketListenerImpl game
                && AerogelRuntime.routeEntityTask(game.player, listener::tick)) return;
            listener.tick();
        }
    }

    @Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
    private static void aerogel$freezeRestartInput(
        Packet<?> packet, PacketListener listener, CallbackInfo callbackInfo
    ) {
        if (RestartCoordinator.suppressInbound(listener)) callbackInfo.cancel();
    }

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
