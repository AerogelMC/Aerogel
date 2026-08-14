package net.minecraft.server.level;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class ServerEntity {
    public ServerEntity(ServerLevel level, Entity entity, int updateInterval, boolean trackDelta,
                        Synchronizer synchronizer) { }

    public void addPairing(ServerPlayer player) { }

    public void sendPairingData(ServerPlayer player,
                                Consumer<Packet<ClientGamePacketListener>> packetConsumer) { }

    public interface Synchronizer {
        void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet);
        void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet);
        void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet,
                                           Predicate<ServerPlayer> filter);
    }
}
