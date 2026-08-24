package dev.aerogel.loader.mixin.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
        ConcurrentHashMap<WaypointTransmitter, WaypointState>>
        aerogel$connections = new ConcurrentHashMap<>();
    @Unique private final SpatialIndex<WaypointTransmitter> aerogel$waypointIndex =
        new SpatialIndex<>();
    @Unique private final SpatialIndex<ServerPlayer> aerogel$playerIndex =
        new SpatialIndex<>();
    @Unique private final ConcurrentHashMap<WaypointTransmitter, Set<WaypointState>>
        aerogel$reverseConnections = new ConcurrentHashMap<>();

    /** @author Aerogel @reason Isolate waypoint state by its Context-owned transmitter. */
    @Overwrite
    public void trackWaypoint(WaypointTransmitter transmitter) {
        if (transmitter instanceof ServerPlayer player && !aerogel$players.contains(player)) {
            return;
        }
        aerogel$waypoints.add(transmitter);
        if (!(transmitter instanceof LivingEntity source)) {
            for (ServerPlayer player : aerogel$players) aerogel$create(player, transmitter);
            return;
        }
        aerogel$waypointIndex.update(transmitter, source.position());
        aerogel$playerIndex.forEachNear(source.position(),
            source.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE),
            player -> aerogel$create(player, transmitter));
    }

    /** @author Aerogel @reason Update only the owning transmitter row. */
    @Overwrite
    public void updateWaypoint(WaypointTransmitter transmitter) {
        if (!aerogel$waypoints.contains(transmitter)) return;
        Set<WaypointState> connected = aerogel$reverseConnections.get(transmitter);
        if (connected != null) {
            for (WaypointState state : connected) state.requestUpdate();
        }
        if (!(transmitter instanceof LivingEntity source)) {
            for (ServerPlayer player : aerogel$players) aerogel$create(player, transmitter);
            return;
        }
        aerogel$waypointIndex.update(transmitter, source.position());
        aerogel$playerIndex.forEachNear(source.position(),
            source.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE),
            player -> aerogel$create(player, transmitter));
    }

    /** @author Aerogel @reason Atomically retire one transmitter row. */
    @Overwrite
    public void untrackWaypoint(WaypointTransmitter transmitter) {
        aerogel$waypoints.remove(transmitter);
        aerogel$waypointIndex.remove(transmitter);
        Set<WaypointState> connected = aerogel$reverseConnections.remove(transmitter);
        if (connected != null) for (WaypointState state : connected) state.close();
    }

    /** @author Aerogel @reason Publish one player across independent transmitter rows. */
    @Overwrite
    public void addPlayer(ServerPlayer player) {
        aerogel$connections.putIfAbsent(player, new ConcurrentHashMap<>());
        aerogel$players.add(player);
        aerogel$playerIndex.update(player, player.position());
        aerogel$waypointIndex.forEachNear(player.position(),
            player.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE),
            transmitter -> aerogel$create(player, transmitter));
        if (player.isTransmittingWaypoint()) trackWaypoint(player);
    }

    /** @author Aerogel @reason Reconcile one player without touching unrelated rows. */
    @Overwrite
    public void updatePlayer(ServerPlayer player) {
        if (!aerogel$players.contains(player)) return;
        aerogel$playerIndex.update(player, player.position());
        ConcurrentHashMap<WaypointTransmitter, WaypointState> row =
            aerogel$connections.get(player);
        if (row == null) return;
        for (WaypointState state : row.values()) state.requestUpdate();
        aerogel$waypointIndex.forEachNear(player.position(),
            player.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE),
            transmitter -> aerogel$create(player, transmitter));
    }

    /** @author Aerogel @reason Remove only this viewer column, then its transmitter row. */
    @Overwrite
    public void removePlayer(ServerPlayer player) {
        aerogel$playerIndex.remove(player);
        ConcurrentHashMap<WaypointTransmitter, WaypointState> row =
            aerogel$connections.remove(player);
        if (row != null) row.values().forEach(WaypointState::close);
        untrackWaypoint(player);
        aerogel$players.remove(player);
    }

    /** @author Aerogel @reason Retire every independent row without a global table iterator. */
    @Overwrite
    public void breakAllConnections() {
        for (ConcurrentHashMap<WaypointTransmitter, WaypointState> row
            : aerogel$connections.values()) {
            row.values().forEach(WaypointState::close);
            row.clear();
        }
    }

    /** @author Aerogel @reason Rebuild only the requested transmitter row. */
    @Overwrite
    public void remakeConnections(WaypointTransmitter transmitter) {
        updateWaypoint(transmitter);
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
        ConcurrentHashMap<WaypointTransmitter, WaypointState> row =
            aerogel$connections.get(player);
        if (row == null) return;
        WaypointState existing = row.get(transmitter);
        if (existing != null) return;
        WaypointTransmitter.Connection connection =
            transmitter.makeWaypointConnectionWith(player).orElse(null);
        if (connection == null) return;
        Set<WaypointState> reverse = aerogel$reverseConnections.get(transmitter);
        if (reverse == null) {
            Set<WaypointState> createdReverse = ConcurrentHashMap.newKeySet();
            Set<WaypointState> raced = aerogel$reverseConnections.putIfAbsent(
                transmitter, createdReverse);
            reverse = raced == null ? createdReverse : raced;
        }
        WaypointState created = new WaypointState(row, reverse,
            aerogel$reverseConnections, player, transmitter, connection);
        existing = row.putIfAbsent(transmitter, created);
        if (existing == null) {
            reverse.add(created);
            if (aerogel$players.contains(player) && aerogel$waypoints.contains(transmitter)) {
                created.requestUpdate();
            } else {
                created.close();
            }
        }
    }

    @Unique
    private void aerogel$update(ServerPlayer player, WaypointTransmitter transmitter) {
        if (player == transmitter || !aerogel$locatorBarEnabled(player)
            || !aerogel$players.contains(player) || !aerogel$waypoints.contains(transmitter)) return;
        aerogel$create(player, transmitter);
    }

    @Unique
    private static boolean aerogel$locatorBarEnabled(ServerPlayer player) {
        return player.level().getGameRules().get(GameRules.LOCATOR_BAR);
    }

    /** Serializes one active receiver/transmitter pair without a monitor or map-bin lock. */
    @Unique
    private static final class WaypointState {
        private final ConcurrentHashMap<WaypointTransmitter, WaypointState> row;
        private final Set<WaypointState> reverse;
        private final ConcurrentHashMap<WaypointTransmitter, Set<WaypointState>> reverseIndex;
        private final ServerPlayer player;
        private final WaypointTransmitter transmitter;
        private final AtomicInteger work = new AtomicInteger();
        private volatile boolean closed;
        private WaypointTransmitter.Connection connection;
        private boolean connected;

        private WaypointState(
            ConcurrentHashMap<WaypointTransmitter, WaypointState> row,
            Set<WaypointState> reverse,
            ConcurrentHashMap<WaypointTransmitter, Set<WaypointState>> reverseIndex,
            ServerPlayer player,
            WaypointTransmitter transmitter,
            WaypointTransmitter.Connection connection
        ) {
            this.row = row;
            this.reverse = reverse;
            this.reverseIndex = reverseIndex;
            this.player = player;
            this.transmitter = transmitter;
            this.connection = connection;
        }

        private void requestUpdate() {
            signal();
        }

        private void close() {
            closed = true;
            row.remove(transmitter, this);
            removeReverse();
            signal();
        }

        private void signal() {
            if (work.getAndIncrement() != 0) return;
            int missed = 1;
            do {
                if (closed) {
                    disconnect();
                } else {
                    reconcile();
                }
                missed = work.addAndGet(-missed);
            } while (missed != 0);
        }

        private void reconcile() {
            WaypointTransmitter.Connection current = connection;
            if (!connected) {
                current.connect();
                connected = true;
                return;
            }
            if (!current.isBroken()) {
                current.update();
                return;
            }
            WaypointTransmitter.Connection replacement =
                transmitter.makeWaypointConnectionWith(player).orElse(null);
            if (replacement != null) {
                connection = replacement;
                replacement.connect();
                return;
            }
            current.disconnect();
            connected = false;
            closed = true;
            row.remove(transmitter, this);
            removeReverse();
        }

        private void disconnect() {
            if (!connected) return;
            connected = false;
            connection.disconnect();
        }

        private void removeReverse() {
            reverse.remove(this);
            if (reverse.isEmpty()) reverseIndex.remove(transmitter, reverse);
        }
    }

    /** Chunk-sized spatial publication; queries use the exact dynamic attribute range. */
    @Unique
    private static final class SpatialIndex<T> {
        private final ConcurrentHashMap<T, Long> positions = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, Set<T>> cells = new ConcurrentHashMap<>();

        private void update(T value, Vec3 position) {
            long next = key((int) Math.floor(position.x) >> 4,
                (int) Math.floor(position.z) >> 4);
            Long previous = positions.get(value);
            if (previous != null && previous.longValue() == next) return;
            Set<T> nextCell = cells.get(next);
            if (nextCell == null) {
                Set<T> created = ConcurrentHashMap.newKeySet();
                Set<T> raced = cells.putIfAbsent(next, created);
                nextCell = raced == null ? created : raced;
            }
            nextCell.add(value);
            positions.put(value, next);
            if (previous != null) removeFromCell(previous, value);
        }

        private void remove(T value) {
            Long previous = positions.remove(value);
            if (previous != null) removeFromCell(previous, value);
        }

        private void forEachNear(Vec3 origin, double range, Consumer<T> action) {
            if (!(range > 0.0) || positions.isEmpty()) return;
            if (!Double.isFinite(range)) {
                positions.keySet().forEach(action);
                return;
            }
            int minX = (int) Math.floor(origin.x - range) >> 4;
            int maxX = (int) Math.floor(origin.x + range) >> 4;
            int minZ = (int) Math.floor(origin.z - range) >> 4;
            int maxZ = (int) Math.floor(origin.z + range) >> 4;
            long width = (long) maxX - minX + 1L;
            long depth = (long) maxZ - minZ + 1L;
            long cellCount;
            try {
                cellCount = Math.multiplyExact(width, depth);
            } catch (ArithmeticException overflow) {
                positions.keySet().forEach(action);
                return;
            }
            if (cellCount >= positions.mappingCount()) {
                positions.keySet().forEach(action);
                return;
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Set<T> cell = cells.get(key(x, z));
                    if (cell != null) cell.forEach(action);
                    if (z == Integer.MAX_VALUE) break;
                }
                if (x == Integer.MAX_VALUE) break;
            }
        }

        private void removeFromCell(long key, T value) {
            Set<T> cell = cells.get(key);
            if (cell == null) return;
            cell.remove(value);
            if (cell.isEmpty()) cells.remove(key, cell);
        }

        private static long key(int x, int z) {
            return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
        }
    }
}
