package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.restart.RestartAddressRegistry;
import dev.aerogel.loader.restart.RestartCoordinator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerHandshakePacketListenerImpl")
abstract class ServerHandshakePacketListenerMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void aerogel$rememberRequestedAddress(
        ClientIntentionPacket packet, CallbackInfo callbackInfo
    ) {
        RestartAddressRegistry.remember(connection, packet.hostName(), packet.port());
    }

    @Redirect(
        method = "handleIntention",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;acceptsTransfers()Z"
        )
    )
    private boolean aerogel$acceptRestartTransfer(MinecraftServer server) {
        return server.acceptsTransfers() || RestartCoordinator.acceptsRestartTransfer(connection);
    }
}
