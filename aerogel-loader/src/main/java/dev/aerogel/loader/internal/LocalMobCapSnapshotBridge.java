package dev.aerogel.loader.internal;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Installs the immutable player-position view used by one natural-spawn state. */
public interface LocalMobCapSnapshotBridge {
    void aerogel$snapshotPlayers(List<ServerPlayer> players);
}
