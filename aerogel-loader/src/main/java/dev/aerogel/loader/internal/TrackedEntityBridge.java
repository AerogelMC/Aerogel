package dev.aerogel.loader.internal;

import net.minecraft.server.level.ServerPlayer;

public interface TrackedEntityBridge {
    boolean aerogel$isSeenBy(Object connection);
    void aerogel$removePlayer(ServerPlayer player);
    void aerogel$updatePlayer(ServerPlayer player);
}
