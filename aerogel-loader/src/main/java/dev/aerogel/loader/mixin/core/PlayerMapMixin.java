package dev.aerogel.loader.mixin.core;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Publishes player proximity membership for lock-free Context reads. */
@Mixin(targets = "net.minecraft.server.level.PlayerMap")
abstract class PlayerMapMixin {
    @Unique
    private final ConcurrentHashMap<ServerPlayer, Boolean> aerogel$players =
        new ConcurrentHashMap<>();

    /** @author Aerogel @reason Return a weakly-consistent exact membership view. */
    @Overwrite
    public Set<ServerPlayer> getAllPlayers() {
        return aerogel$players.keySet();
    }

    /** @author Aerogel @reason Atomically publish a player and its ignore state. */
    @Overwrite
    public void addPlayer(ServerPlayer player, boolean ignored) {
        aerogel$players.put(player, ignored);
    }

    /** @author Aerogel @reason Atomically withdraw player membership. */
    @Overwrite
    public void removePlayer(ServerPlayer player) {
        aerogel$players.remove(player);
    }

    /** @author Aerogel @reason Update only an existing player's state. */
    @Overwrite
    public void ignorePlayer(ServerPlayer player) {
        aerogel$players.replace(player, true);
    }

    /** @author Aerogel @reason Update only an existing player's state. */
    @Overwrite
    public void unIgnorePlayer(ServerPlayer player) {
        aerogel$players.replace(player, false);
    }

    /** @author Aerogel @reason Read the published ignore state without map corruption. */
    @Overwrite
    public boolean ignoredOrUnknown(ServerPlayer player) {
        return aerogel$players.getOrDefault(player, true);
    }

    /** @author Aerogel @reason Read the published ignore state without map corruption. */
    @Overwrite
    public boolean ignored(ServerPlayer player) {
        return Boolean.TRUE.equals(aerogel$players.get(player));
    }
}
