package net.minecraft.world.waypoints;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface WaypointTransmitter {
    Optional<Connection> makeWaypointConnectionWith(ServerPlayer player);

    interface Connection {
        void connect();
        void disconnect();
        void update();
        boolean isBroken();
    }
}
