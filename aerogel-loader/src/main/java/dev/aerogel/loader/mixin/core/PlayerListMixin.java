package dev.aerogel.loader.mixin.core;

import dev.aerogel.api.event.player.PlayerJoinEvent;
import dev.aerogel.api.event.player.PlayerLoginEvent;
import dev.aerogel.api.event.player.PlayerRespawnEvent;
import dev.aerogel.loader.event.EventHooks;
import dev.aerogel.loader.restart.RestartCoordinator;
import dev.aerogel.loader.internal.PlayerNameTagService;
import dev.aerogel.loader.internal.ConcurrentSnapshotList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.server.players.PlayerList")
abstract class PlayerListMixin {
    @Shadow @Final @Mutable private List<ServerPlayer> players;
    @Shadow @Final @Mutable private Map<UUID, ServerPlayer> playersByUUID;
    @Shadow @Final @Mutable private Map<UUID, ?> stats;
    @Shadow @Final @Mutable private Map<UUID, ?> advancements;
    @Unique private Component aerogel$pendingJoinMessage;
    @Unique private boolean aerogel$pendingJoinOverlay;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$installConcurrentPlayerCollections(CallbackInfo callbackInfo) {
        players = new ConcurrentSnapshotList<>(players);
        playersByUUID = new ConcurrentHashMap<>(playersByUUID);
        stats = new ConcurrentHashMap<>(stats);
        advancements = new ConcurrentHashMap<>(advancements);
    }

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void aerogel$loginCheck(
        java.net.SocketAddress address,
        NameAndId nameAndId,
        CallbackInfoReturnable<Component> callbackInfo
    ) {
        if (!EventHooks.hasListeners(PlayerLoginEvent.class)) return;
        PlayerLoginEvent event = new PlayerLoginEvent(
            nameAndId.id(), nameAndId.name(),
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
    private void aerogel$handleJoinAnnouncement(
        PlayerList players,
        Component message,
        boolean overlay,
        Connection connection,
        ServerPlayer player,
        @Coerce Object cookie
    ) {
        if (RestartCoordinator.isReturningPlayer(player)) return;
        if (!EventHooks.hasListeners(PlayerJoinEvent.class)) {
            players.broadcastSystemMessage(message, overlay);
            return;
        }

        // Vanilla announces the join before its placement routine has fully returned. Hold that
        // one announcement until RETURN so PlayerJoinEvent retains its lifecycle semantics.
        aerogel$pendingJoinMessage = message;
        aerogel$pendingJoinOverlay = overlay;
    }

    @Inject(
        method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
        at = @At("RETURN")
    )
    private void aerogel$playerJoined(
        Connection connection,
        ServerPlayer player,
        @Coerce Object cookie,
        CallbackInfo callbackInfo
    ) {
        Component message = aerogel$pendingJoinMessage;
        boolean overlay = aerogel$pendingJoinOverlay;
        aerogel$pendingJoinMessage = null;
        aerogel$pendingJoinOverlay = false;

        try {
            if (message != null && !RestartCoordinator.isReturningPlayer(player)) {
                int updatedPlayerCount = ((PlayerList) (Object) this).getPlayers().size();
                PlayerJoinEvent event = new PlayerJoinEvent(
                    player,
                    connection,
                    message,
                    Math.max(0, updatedPlayerCount - 1),
                    updatedPlayerCount
                );
                EventHooks.post(event);
                if (!event.isCancelled()) {
                    Component announcement = event.message();
                    ((PlayerList) (Object) this).broadcastSystemMessage(
                        announcement,
                        recipient -> recipient == player ? null : announcement,
                        overlay
                    );
                }
            }
        } finally {
            RestartCoordinator.playerJoined(player);
        }
    }

    @Inject(
        method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD")
    )
    private void aerogel$playerQuit(ServerPlayer player, CallbackInfo callbackInfo) {
        PlayerNameTagService.playerRemoved(player);
    }

    @Inject(
        method = "respawn(Lnet/minecraft/server/level/ServerPlayer;Z"
            + "Lnet/minecraft/world/entity/Entity$RemovalReason;)"
            + "Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("RETURN")
    )
    private void aerogel$playerRespawned(
        ServerPlayer previousPlayer,
        boolean keepEverything,
        Entity.RemovalReason removalReason,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ServerPlayer> callbackInfo
    ) {
        if (EventHooks.hasListeners(PlayerRespawnEvent.class)) {
            EventHooks.post(new PlayerRespawnEvent(
                previousPlayer, callbackInfo.getReturnValue(), keepEverything));
        }
    }
}
