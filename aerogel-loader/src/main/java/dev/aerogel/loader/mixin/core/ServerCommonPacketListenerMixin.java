package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.api.DialogCallbackRegistry;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.internal.PlayerViewService;
import dev.aerogel.loader.runtime.AerogelRuntime;
import dev.aerogel.api.event.player.PlayerCustomClickActionEvent;
import dev.aerogel.api.event.player.PlayerCustomPayloadEvent;
import dev.aerogel.api.event.player.PlayerResourcePackStatusEvent;
import dev.aerogel.api.event.player.PlayerPacketEvent;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(targets = "net.minecraft.server.network.ServerCommonPacketListenerImpl")
abstract class ServerCommonPacketListenerMixin {
    @Unique private final AtomicBoolean aerogel$disconnectRequested =
        new AtomicBoolean();

    @Inject(
        method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$claimDisconnect(
        @Coerce Object details, CallbackInfo callbackInfo
    ) {
        // Timeout detection may run again while the first disconnect packet is
        // still crossing an overloaded compression lane. Disconnect is a
        // one-way lifecycle transition: only its first caller may enqueue the
        // packet, close callback, and main-thread disconnection handler.
        if (!aerogel$disconnectRequested.compareAndSet(false, true)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "onPacketError(Lnet/minecraft/network/protocol/Packet;Ljava/lang/Exception;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aerogel$acceptOwnerReroute(
        Packet<?> packet, Exception error, CallbackInfo callbackInfo
    ) {
        // PacketProcessor.ListenerAndPacket catches the same control-flow
        // exception that Connection intentionally ignores. A stale Context has
        // already handed the packet to its current entity owner, so reporting it
        // as a handler failure only creates log and allocation storms.
        if (error instanceof RunningOnDifferentThreadException) {
            callbackInfo.cancel();
        }
    }

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
        return player != null && AerogelRuntime.isEntityMutationThread(player);
    }
}
