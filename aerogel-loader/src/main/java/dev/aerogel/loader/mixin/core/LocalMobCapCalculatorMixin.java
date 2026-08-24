package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import dev.aerogel.loader.internal.LocalMobCapSnapshotBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator")
abstract class LocalMobCapCalculatorMixin implements LocalMobCapSnapshotBridge {
    /** Exact squared threshold used by ChunkMap.playerIsCloseEnoughForSpawning. */
    private static final double AEROGEL$VANILLA_SPAWN_DISTANCE_SQUARED = 16_384.0D;
    @Shadow @Final @Mutable private Long2ObjectMap<Object> playersNearChunk;
    @Shadow @Final @Mutable private Map<Object, Object> playerMobCounts;
    @Unique private volatile PlayerSnapshot aerogel$playerSnapshot;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$useConcurrentIndexes(CallbackInfo callback) {
        playersNearChunk = new ConcurrentLong2ObjectMap<>();
        playerMobCounts = new ConcurrentHashMap<>();
    }

    @Override
    public void aerogel$snapshotPlayers(List<ServerPlayer> players) {
        Long2ObjectOpenHashMap<ArrayList<PlayerSample>> mutableCells =
            new Long2ObjectOpenHashMap<>();
        for (ServerPlayer player : players) {
            Vec3 position = player.position();
            PlayerSample sample = new PlayerSample(
                player, player.isSpectator(), position.x, position.z);
            mutableCells.computeIfAbsent(aerogel$cell(position.x, position.z),
                ignored -> new ArrayList<>()).add(sample);
        }
        Long2ObjectOpenHashMap<PlayerSample[]> cells =
            new Long2ObjectOpenHashMap<>(mutableCells.size());
        for (Long2ObjectMap.Entry<ArrayList<PlayerSample>> entry
            : mutableCells.long2ObjectEntrySet()) {
            cells.put(entry.getLongKey(), entry.getValue().toArray(PlayerSample[]::new));
        }
        // The map and arrays are never mutated after this volatile publication,
        // so parallel readers need neither a concurrent table nor boxed Long keys.
        aerogel$playerSnapshot = new PlayerSnapshot(cells);
    }

    /**
     * The vanilla DistanceManager query mutates its pending distance graph, so it
     * cannot be called by parallel chunk owners. This evaluates the exact final
     * vanilla predicate against the tick-start player snapshot instead.
     */
    @Inject(method = "getPlayersNear", at = @At("HEAD"), cancellable = true)
    private void aerogel$playersNearFromTickSnapshot(
        ChunkPos chunk, CallbackInfoReturnable<List<ServerPlayer>> callback
    ) {
        PlayerSnapshot snapshot = aerogel$playerSnapshot;
        if (snapshot == null) return;
        double centerX = (double) chunk.x() * 16.0D + 8.0D;
        double centerZ = (double) chunk.z() * 16.0D + 8.0D;
        ArrayList<ServerPlayer> nearby = new ArrayList<>();
        int cellX = aerogel$cellCoordinate(centerX);
        int cellZ = aerogel$cellCoordinate(centerZ);
        for (int xCell = cellX - 1; xCell <= cellX + 1; xCell++) {
            for (int zCell = cellZ - 1; zCell <= cellZ + 1; zCell++) {
                PlayerSample[] candidates = snapshot.cells.get(
                    aerogel$cellKey(xCell, zCell));
                if (candidates == null) continue;
                for (PlayerSample sample : candidates) {
                    if (sample.spectator) continue;
                    double x = centerX - sample.x;
                    double z = centerZ - sample.z;
                    if (x * x + z * z < AEROGEL$VANILLA_SPAWN_DISTANCE_SQUARED) {
                        nearby.add(sample.player);
                    }
                }
            }
        }
        callback.setReturnValue(nearby.isEmpty() ? List.of() : nearby);
    }

    @Unique
    private static long aerogel$cell(double x, double z) {
        return aerogel$cellKey(aerogel$cellCoordinate(x), aerogel$cellCoordinate(z));
    }

    @Unique
    private static int aerogel$cellCoordinate(double coordinate) {
        // The cell side is the exact vanilla spawning radius. Any point passing
        // the final squared-distance predicate must be in this cell or a neighbor.
        double radius = Math.sqrt(AEROGEL$VANILLA_SPAWN_DISTANCE_SQUARED);
        return (int) Math.floor(coordinate / radius);
    }

    @Unique
    private static long aerogel$cellKey(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    @Unique
    private record PlayerSample(
        ServerPlayer player, boolean spectator, double x, double z
    ) { }

    @Unique
    private record PlayerSnapshot(
        Long2ObjectMap<PlayerSample[]> cells
    ) { }
}
