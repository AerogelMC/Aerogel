package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.api.DialogCallbackRegistry;
import dev.aerogel.loader.event.EventHooks;
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
        Object player;
        try { player = EventHooks.call(this, "getPlayer"); }
        catch (IllegalStateException ignored) { return; }
        if (DialogCallbackRegistry.dispatch(id, player, EventHooks.call(packet, "payload"))) {
            callbackInfo.cancel();
        }
    }
}
