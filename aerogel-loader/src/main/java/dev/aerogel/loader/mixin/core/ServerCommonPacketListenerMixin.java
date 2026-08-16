package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.api.DialogCallbackRegistry;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.PlayerViewService;
import dev.aerogel.api.event.player.PlayerCustomClickActionEvent;
import dev.aerogel.api.event.player.PlayerCustomPayloadEvent;
import dev.aerogel.api.event.player.PlayerResourcePackStatusEvent;
import dev.aerogel.api.event.player.PlayerPacketEvent;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerCommonPacketListenerImpl")
abstract class ServerCommonPacketListenerMixin {
    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"), argsOnly = true
    )
    private net.minecraft.network.protocol.Packet<?> aerogel$applyViewerOverrides(
        net.minecraft.network.protocol.Packet<?> packet
    ) {
        ServerPlayer player = playerOrNull();
        return player == null ? packet : PlayerViewService.transform(player, packet);
    }

    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void aerogel$dialogAction(
        ServerboundCustomClickActionPacket packet, CallbackInfo callbackInfo
    ) {
        String id = packet.id().toString();
        ServerPlayer player = playerOrNull();
        if (!aerogel$serverThread(player)) return;
        if (EventHooks.hasListeners(PlayerCustomClickActionEvent.class)) {
            PlayerCustomClickActionEvent event = new PlayerCustomClickActionEvent(
                player, packet);
            EventHooks.post(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
                return;
            }
        }
        if (DialogCallbackRegistry.dispatch(id, player, packet.payload())) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void aerogel$customPayload(@Coerce Object packet, CallbackInfo callbackInfo) {
        ServerPlayer player = playerOrNull();
        if (aerogel$serverThread(player) && EventHooks.hasListeners(PlayerCustomPayloadEvent.class)) {
            post(new PlayerCustomPayloadEvent(player, EventHooks.cast(packet)), callbackInfo);
        }
    }

    @Inject(method = "handleResourcePackResponse", at = @At("HEAD"), cancellable = true)
    private void aerogel$resourcePackResponse(@Coerce Object packet, CallbackInfo callbackInfo) {
        ServerPlayer player = playerOrNull();
        if (aerogel$serverThread(player) && EventHooks.hasListeners(PlayerResourcePackStatusEvent.class)) {
            post(new PlayerResourcePackStatusEvent(player, EventHooks.cast(packet)), callbackInfo);
        }
    }

    private void post(PlayerPacketEvent event, CallbackInfo callbackInfo) {
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    private ServerPlayer playerOrNull() {
        return (Object) this instanceof ServerGamePacketListenerImpl listener
            ? listener.player : null;
    }

    @Unique
    private static boolean aerogel$serverThread(ServerPlayer player) {
        return player != null && player.level().getServer().isSameThread();
    }
}
