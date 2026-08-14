package dev.aerogel.loader.internal;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

enum NoopServerEntitySynchronizer implements ServerEntity.Synchronizer {
    INSTANCE;

    @Override
    public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
    }

    @Override
    public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
    }

    @Override
    public void sendToTrackingPlayersFiltered(
        Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> filter
    ) {
    }
}
