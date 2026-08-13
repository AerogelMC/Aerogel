package dev.aerogel.api.player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Direct access to online vanilla ServerPlayer instances. */
public interface PlayerService {
    Collection<ServerPlayer> online();
    Optional<ServerPlayer> find(String name);
    Optional<ServerPlayer> find(UUID uniqueId);
    void broadcast(Component message);
    void message(ServerPlayer vanillaPlayer, Component message);
    void actionBar(ServerPlayer vanillaPlayer, Component message);
}
