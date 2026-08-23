package dev.aerogel.loader.mixin.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Splits the world-wide waypoint connection table into player-owned rows.
 * Vanilla's HashBasedTable assumes that every player and transmitter is ticked by one thread;
 * Context-owned player ticks invalidate that assumption. A player update now mutates only that
 * player's row. A transmitter update visits independent rows, so unrelated player Contexts never
 * contend on one popular transmitter's map.
 */
@Mixin(ServerWaypointManager.class)
abstract class ServerWaypointManagerMixin {
    @Unique
    private final Set<WaypointTransmitter> aerogel$waypoints =
        ConcurrentHashMap.newKeySet();
    @Unique
    private final Set<ServerPlayer> aerogel$players =
        ConcurrentHashMap.newKeySet();
    @Unique
    private final ConcurrentHashMap<ServerPlayer,
        ConcurrentHashMap<WaypointTransmitter, WaypointTransmitter.Connection>>
        aerogel$connections = new ConcurrentHashMap<>();

    /** @author Aerogel @reason Isolate waypoint state by its Context-owned transmitter. */
    @Overwrite
    public void trackWaypoint(WaypointTransmitter transmitter) {
        if (transmitter instanceof ServerPlayer player && !aerogel$players.contains(player)) {
            return;
        }
        aerogel$waypoints.add(transmitter);
        for (ServerPlayer player : aerogel$players) {
            aerogel$create(player, transmitter);
        }
    }

    /** @author Aerogel @reason Update only the owning transmitter row. */
    @Overwrite
    public void updateWaypoint(WaypointTransmitter transmitter) {
        if (!aerogel$waypoints.contains(transmitter)) return;
        for (ServerPlayer player : aerogel$players) {
            aerogel$update(player, transmitter);
        }
    }

    /** @author Aerogel @reason Atomically retire one transmitter row. */
    @Overwrite
    public void untrackWaypoint(WaypointTransmitter transmitter) {
        aerogel$waypoints.remove(transmitter);
        for (ConcurrentHashMap<WaypointTransmitter, WaypointTransmitter.Connection> row
            : aerogel$connections.values()) {
            WaypointTransmitter.Connection connection = row.remove(transmitter);
            if (connection != null) connection.disconnect();
        }
    }

    /** @author Aerogel @reason Publish one player across independent transmitter rows. */
    @Overwrite
    public void addPlayer(ServerPlayer player) {
        aerogel$players.add(player);
        aerogel$connections.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
        for (WaypointTransmitter transmitter : aerogel$waypoints) {
            aerogel$create(player, transmitter);
        }
        if (player.isTransmittingWaypoint()) trackWaypoint(player);
    }

    /** @author Aerogel @reason Reconcile one player without touching unrelated rows. */
    @Overwrite
    public void updatePlayer(ServerPlayer player) {
        if (!aerogel$players.contains(player)) return;
        for (WaypointTransmitter transmitter : aerogel$waypoints) aerogel$update(player, transmitter);
    }

    /** @author Aerogel @reason Remove only this viewer column, then its transmitter row. */
    @Overwrite
    public void removePlayer(ServerPlayer player) {
        ConcurrentHashMap<WaypointTransmitter, WaypointTransmitter.Connection> row =
            aerogel$connections.remove(player);
        if (row != null) row.values().forEach(WaypointTransmitter.Connection::disconnect);
        untrackWaypoint(player);
        aerogel$players.remove(player);
    }

    /** @author Aerogel @reason Retire every independent row without a global table iterator. */
    @Overwrite
    public void breakAllConnections() {
        for (ConcurrentHashMap<WaypointTransmitter, WaypointTransmitter.Connection> row
            : aerogel$connections.values()) {
            row.values().forEach(WaypointTransmitter.Connection::disconnect);
            row.clear();
        }
    }

    /** @author Aerogel @reason Rebuild only the requested transmitter row. */
    @Overwrite
    public void remakeConnections(WaypointTransmitter transmitter) {
        for (ServerPlayer player : aerogel$players) aerogel$create(player, transmitter);
    }

    /** @author Aerogel @reason Expose the concurrent transmitter registry. */
    @Overwrite
    public Set<WaypointTransmitter> transmitters() {
        return aerogel$waypoints;
    }

    @Unique
    private void aerogel$create(ServerPlayer player, WaypointTransmitter transmitter) {
        if (player == transmitter || !aerogel$locatorBarEnabled(player)
            || !aerogel$players.contains(player) || !aerogel$waypoints.contains(transmitter)) return;
        ConcurrentHashMap<WaypointTransmitter, WaypointTransmitter.Connection> row =
            aerogel$connections.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
        row.compute(transmitter, (ignored, existing) -> {
            WaypointTransmitter.Connection replacement =
                transmitter.makeWaypointConnectionWith(player).orElse(null);
            if (replacement == null) {
                if (existing != null) existing.disconnect();
                return null;
            }
            replacement.connect();
            return replacement;
        });
    }

    @Unique
    private void aerogel$update(ServerPlayer player, WaypointTransmitter transmitter) {
        if (player == transmitter || !aerogel$locatorBarEnabled(player)
            || !aerogel$players.contains(player) || !aerogel$waypoints.contains(transmitter)) return;
        ConcurrentHashMap<WaypointTransmitter, WaypointTransmitter.Connection> row =
            aerogel$connections.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>());
        row.compute(transmitter, (ignored, existing) -> {
            if (existing != null && !existing.isBroken()) {
                existing.update();
                return existing;
            }
            WaypointTransmitter.Connection replacement =
                transmitter.makeWaypointConnectionWith(player).orElse(null);
            if (replacement == null) {
                if (existing != null) existing.disconnect();
                return null;
            }
            if (existing != null) existing.disconnect();
            replacement.connect();
            return replacement;
        });
    }

    @Unique
    private static boolean aerogel$locatorBarEnabled(ServerPlayer player) {
        return player.level().getGameRules().get(GameRules.LOCATOR_BAR);
    }
}
