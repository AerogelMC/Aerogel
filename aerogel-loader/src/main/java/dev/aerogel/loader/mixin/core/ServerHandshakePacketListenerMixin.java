package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.restart.RestartAddressRegistry;
import dev.aerogel.loader.restart.RestartCoordinator;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerHandshakePacketListenerImpl")
abstract class ServerHandshakePacketListenerMixin {
    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void aerogel$rememberRequestedAddress(@Coerce Object packet, CallbackInfo callbackInfo) {
        Object connection = EventHooks.field(this, "connection");
        String host = (String) EventHooks.call(packet, "hostName");
        int port = (int) EventHooks.call(packet, "port");
        RestartAddressRegistry.remember(connection, host, port);
    }

    @Redirect(
        method = "handleIntention",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;acceptsTransfers()Z"
        )
    )
    private boolean aerogel$acceptRestartTransfer(MinecraftServer server) {
        Object connection = EventHooks.field(this, "connection");
        return server.acceptsTransfers() || RestartCoordinator.acceptsRestartTransfer(connection);
    }
}
