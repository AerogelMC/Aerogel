package dev.aerogel.loader.internal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public interface TrackedEntityBridge {
    Entity aerogel$entity();
    boolean aerogel$sectionChanged();
    void aerogel$tickTracking(
        List<ServerPlayer> players, DistanceManagerBridge distanceManager);
    void aerogel$updatePlayers(List<ServerPlayer> players);
    boolean aerogel$isSeenBy(Object connection);
    void aerogel$removePlayer(ServerPlayer player);
    void aerogel$updatePlayer(ServerPlayer player);
}
