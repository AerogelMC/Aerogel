package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.player.PlayerJoinEvent;
import dev.aerogel.api.event.player.PlayerLoginEvent;
import dev.aerogel.api.event.player.PlayerQuitEvent;
import dev.aerogel.api.event.player.PlayerRespawnEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.restart.RestartCoordinator;
import dev.aerogel.loader.internal.PlayerNameTagService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.players.PlayerList")
abstract class PlayerListMixin {
    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void aerogel$loginCheck(
        java.net.SocketAddress address,
        @Coerce Object nameAndId,
        CallbackInfoReturnable<Component> callbackInfo
    ) {
        PlayerLoginEvent event = new PlayerLoginEvent(
            (java.util.UUID) EventHooks.call(nameAndId, "id"),
            (String) EventHooks.call(nameAndId, "name"),
            address,
            callbackInfo.getReturnValue());
        EventHooks.post(event);
        callbackInfo.setReturnValue(event.denialReason());
    }

    @Redirect(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        )
    )
    private void aerogel$suppressRestartJoinMessage(
        PlayerList players,
        Component message,
        boolean overlay,
        Connection connection,
        ServerPlayer player,
        @Coerce Object cookie
    ) {
        if (!RestartCoordinator.isReturningPlayer(player)) {
            players.broadcastSystemMessage(message, overlay);
        }
    }

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
        if (!RestartCoordinator.isReturningPlayer(player)) {
            EventHooks.post(new PlayerJoinEvent(
                EventHooks.cast(player), EventHooks.cast(connection)));
        }
        RestartCoordinator.playerJoined(player);
    }

    @Inject(
        method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD")
    )
    private void aerogel$playerQuit(@Coerce Object player, CallbackInfo callbackInfo) {
        PlayerNameTagService.playerRemoved(EventHooks.cast(player));
        if (!RestartCoordinator.requested()) {
            EventHooks.post(new PlayerQuitEvent(EventHooks.cast(player)));
        }
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
