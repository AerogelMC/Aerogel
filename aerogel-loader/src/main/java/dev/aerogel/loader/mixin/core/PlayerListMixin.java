package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.player.PlayerJoinEvent;
import dev.aerogel.api.event.player.PlayerQuitEvent;
import dev.aerogel.api.event.player.PlayerRespawnEvent;
import dev.aerogel.loader.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.players.PlayerList")
abstract class PlayerListMixin {
    @Inject(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At("RETURN")
    )
    private void aerogel$playerJoined(
        @Coerce Object connection,
        @Coerce Object player,
        @Coerce Object cookie,
        CallbackInfo callbackInfo
    ) {
        EventHooks.post(new PlayerJoinEvent(
            EventHooks.cast(player), EventHooks.cast(connection)));
    }

    @Inject(
        method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD")
    )
    private void aerogel$playerQuit(@Coerce Object player, CallbackInfo callbackInfo) {
        EventHooks.post(new PlayerQuitEvent(EventHooks.cast(player)));
    }

    @Inject(
        method = "respawn(Lnet/minecraft/server/level/ServerPlayer;Z"
            + "Lnet/minecraft/world/entity/Entity$RemovalReason;)"
            + "Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("RETURN")
    )
    private void aerogel$playerRespawned(
        @Coerce Object previousPlayer,
        boolean keepEverything,
        @Coerce Object removalReason,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object> callbackInfo
    ) {
        EventHooks.post(new PlayerRespawnEvent(
            EventHooks.cast(previousPlayer), EventHooks.cast(callbackInfo.getReturnValue()), keepEverything));
    }
}
