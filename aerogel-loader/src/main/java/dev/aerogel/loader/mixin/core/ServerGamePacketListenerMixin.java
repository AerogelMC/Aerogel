package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.inventory.InventoryClickEvent;
import dev.aerogel.api.event.player.PlayerActionEvent;
import dev.aerogel.api.event.player.PlayerChatEvent;
import dev.aerogel.api.event.player.PlayerPacketEvent;
import dev.aerogel.api.event.player.PlayerUseItemEvent;
import dev.aerogel.api.event.player.PlayerUseItemOnBlockEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.network.ServerGamePacketListenerImpl")
abstract class ServerGamePacketListenerMixin {
    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$chat(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerChatEvent(EventHooks.field(this, "player"), packet), callbackInfo);
    }

    @Inject(method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$useItem(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerUseItemEvent(EventHooks.field(this, "player"), packet), callbackInfo);
    }

    @Inject(method = "handleUseItemOn(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$useItemOn(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerUseItemOnBlockEvent(EventHooks.field(this, "player"), packet), callbackInfo);
    }

    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$action(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new PlayerActionEvent(EventHooks.field(this, "player"), packet), callbackInfo);
    }

    @Inject(method = "handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void aerogel$inventoryClick(@Coerce Object packet, CallbackInfo callbackInfo) {
        post(new InventoryClickEvent(EventHooks.field(this, "player"), packet), callbackInfo);
    }

    private static void post(PlayerPacketEvent event, CallbackInfo callbackInfo) {
        EventHooks.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
