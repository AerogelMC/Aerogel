package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.NaturalSpawnDistanceBridge;
import dev.aerogel.loader.context.PaddedAtomicLong;
import net.minecraft.util.TriState;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import java.util.Objects;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
abstract class FixedPlayerDistanceChunkTrackerMixin
    implements NaturalSpawnDistanceBridge {

    @Shadow @Final protected int maxDistance;
    @Shadow protected abstract int getLevel(long chunkKey);
    @Unique private final PaddedAtomicLong aerogel$spawnDistanceVersion =
        new PaddedAtomicLong();
    @Unique private volatile LongConsumer aerogel$spawnDistanceListener = ignored -> { };
    @Unique private long aerogel$pendingChangedKey;
    @Unique private boolean aerogel$pendingChanged;

    @Inject(method = "setLevel(JI)V", at = @At("HEAD"))
    private void aerogel$captureSpawnDistanceChange(
        long chunkKey, int level, CallbackInfo callback
    ) {
        int publishedLevel = level > maxDistance ? maxDistance + 2 : level;
        aerogel$pendingChangedKey = chunkKey;
        aerogel$pendingChanged = getLevel(chunkKey) != publishedLevel;
    }

    @Inject(method = "setLevel(JI)V", at = @At("RETURN"))
    private void aerogel$publishSpawnDistanceChange(
        long chunkKey, int level, CallbackInfo callback
    ) {
        if (!aerogel$pendingChanged || aerogel$pendingChangedKey != chunkKey) return;
        aerogel$pendingChanged = false;
        aerogel$spawnDistanceVersion.incrementAndGet();
        aerogel$spawnDistanceListener.accept(chunkKey);
    }

    @Override
    public TriState aerogel$publishedPlayersNearby(long chunkKey) {
        int level = getLevel(chunkKey);
        if (level <= NaturalSpawner.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK) {
            return TriState.TRUE;
        }
        return level > maxDistance ? TriState.FALSE : TriState.DEFAULT;
    }

    @Override
    public long aerogel$spawnDistanceVersion() {
        return aerogel$spawnDistanceVersion.get();
    }

    @Override
    public void aerogel$spawnDistanceListener(LongConsumer listener) {
        aerogel$spawnDistanceListener = Objects.requireNonNull(listener, "listener");
    }
}
