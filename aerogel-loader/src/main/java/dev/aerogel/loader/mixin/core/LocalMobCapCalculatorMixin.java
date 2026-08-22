package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import dev.aerogel.loader.internal.LocalMobCapSnapshotBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
    @Unique private volatile List<PlayerSample> aerogel$playerSnapshot;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$useConcurrentIndexes(CallbackInfo callback) {
        playersNearChunk = new ConcurrentLong2ObjectMap<>();
        playerMobCounts = new ConcurrentHashMap<>();
    }

    @Override
    public void aerogel$snapshotPlayers(List<ServerPlayer> players) {
        ArrayList<PlayerSample> snapshot = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            Vec3 position = player.position();
            snapshot.add(new PlayerSample(
                player, player.isSpectator(), position.x, position.z));
        }
        aerogel$playerSnapshot = List.copyOf(snapshot);
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
        List<PlayerSample> snapshot = aerogel$playerSnapshot;
        if (snapshot == null) return;
        double centerX = (double) chunk.x() * 16.0D + 8.0D;
        double centerZ = (double) chunk.z() * 16.0D + 8.0D;
        ArrayList<ServerPlayer> nearby = new ArrayList<>();
        for (PlayerSample sample : snapshot) {
            if (sample.spectator) continue;
            double x = centerX - sample.x;
            double z = centerZ - sample.z;
            if (x * x + z * z < AEROGEL$VANILLA_SPAWN_DISTANCE_SQUARED) {
                nearby.add(sample.player);
            }
        }
        callback.setReturnValue(List.copyOf(nearby));
    }

    @Unique
    private record PlayerSample(
        ServerPlayer player, boolean spectator, double x, double z
    ) { }
}
