package dev.aerogel.loader.api;

import dev.aerogel.api.player.PlayerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ReflectivePlayerService implements PlayerService {
    private final PluginApiScope scope;
    ReflectivePlayerService(PluginApiScope scope) { this.scope = scope; }

    private Object list() { return Reflect.invoke(scope.serverHandle(), "getPlayerList"); }
    @Override @SuppressWarnings("unchecked") public Collection<ServerPlayer> online() {
        return List.copyOf((Collection<ServerPlayer>) Reflect.invoke(list(), "getPlayers"));
    }
    @Override public Optional<ServerPlayer> find(String name) {
        return Optional.ofNullable((ServerPlayer) Reflect.invoke(list(), "getPlayerByName", name));
    }
    @Override public Optional<ServerPlayer> find(UUID uniqueId) {
        return Optional.ofNullable((ServerPlayer) Reflect.invoke(list(), "getPlayer", uniqueId));
    }
    @Override public void broadcast(Component message) {
        Reflect.invoke(list(), "broadcastSystemMessage", message, false);
    }
    @Override public void message(ServerPlayer player, Component message) {
        Reflect.invoke(player, "sendSystemMessage", message);
    }
    @Override public void actionBar(ServerPlayer player, Component message) {
        Reflect.invoke(player, "sendOverlayMessage", message);
    }
}
