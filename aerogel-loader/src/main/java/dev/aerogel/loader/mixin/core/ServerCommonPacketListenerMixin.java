package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.api.DialogCallbackRegistry;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.api.event.player.PlayerCustomClickActionEvent;
import dev.aerogel.api.event.player.PlayerCustomPayloadEvent;
import dev.aerogel.api.event.player.PlayerResourcePackStatusEvent;
import dev.aerogel.api.event.player.PlayerPacketEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerCommonPacketListenerImpl")
abstract class ServerCommonPacketListenerMixin {
    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void aerogel$dialogAction(@Coerce Object packet, CallbackInfo callbackInfo) {
        String id = String.valueOf(EventHooks.call(packet, "id"));
        net.minecraft.server.level.ServerPlayer player = playerOrNull();
        if (player == null) return;
        PlayerCustomClickActionEvent event = new PlayerCustomClickActionEvent(
            player, EventHooks.cast(packet));
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
            return;
        }
        if (DialogCallbackRegistry.dispatch(id, player, EventHooks.call(packet, "payload"))) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void aerogel$customPayload(@Coerce Object packet, CallbackInfo callbackInfo) {
        net.minecraft.server.level.ServerPlayer player = playerOrNull();
        if (player != null) {
            post(new PlayerCustomPayloadEvent(player, EventHooks.cast(packet)), callbackInfo);
        }
    }

    @Inject(method = "handleResourcePackResponse", at = @At("HEAD"), cancellable = true)
    private void aerogel$resourcePackResponse(@Coerce Object packet, CallbackInfo callbackInfo) {
        net.minecraft.server.level.ServerPlayer player = playerOrNull();
        if (player != null) {
            post(new PlayerResourcePackStatusEvent(player, EventHooks.cast(packet)), callbackInfo);
        }
    }

    private void post(PlayerPacketEvent event, CallbackInfo callbackInfo) {
        EventHooks.post(event);
        if (event.isCancelled()) callbackInfo.cancel();
    }

    private net.minecraft.server.level.ServerPlayer playerOrNull() {
        if (!EventHooks.isInstance(this,
            "net.minecraft.server.network.ServerGamePacketListenerImpl")) {
            return null;
        }
        return EventHooks.cast(EventHooks.field(this, "player"));
    }
}
