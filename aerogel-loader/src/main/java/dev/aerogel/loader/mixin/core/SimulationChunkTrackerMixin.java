package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.PaddedAtomicLong;
import dev.aerogel.loader.context.ConcurrentLongSet;
import dev.aerogel.loader.internal.SimulationChunkTrackerBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.server.level.ChunkLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.SimulationChunkTracker")
abstract class SimulationChunkTrackerMixin implements SimulationChunkTrackerBridge {
    @Shadow protected abstract int getLevel(long key);
    @Unique private final ConcurrentLongSet aerogel$entityTicking =
        new ConcurrentLongSet();
    @Unique private final PaddedAtomicLong aerogel$publicationVersion =
        new PaddedAtomicLong();
    @Unique private volatile LongConsumer aerogel$blockTickingListener = ignored -> { };

    @Inject(method = "setLevel(JI)V", at = @At("HEAD"))
    private void aerogel$publishLevel(long key, int level, CallbackInfo callback) {
        int previous = getLevel(key);
        if (ChunkLevel.isBlockTicking(previous) != ChunkLevel.isBlockTicking(level)) {
            aerogel$blockTickingListener.accept(key);
        }
        boolean wasEntityTicking = ChunkLevel.isEntityTicking(previous);
        boolean entityTicking = ChunkLevel.isEntityTicking(level);
        if (wasEntityTicking == entityTicking) return;

        aerogel$publicationVersion.incrementAndGet();
        if (entityTicking) {
            aerogel$entityTicking.add(key);
        } else {
            aerogel$entityTicking.remove(key);
        }
        aerogel$publicationVersion.incrementAndGet();
    }

    @Override
    public void aerogel$forEachEntityTickingChunk(LongConsumer consumer) {
        long[] snapshot;
        while (true) {
            long before = aerogel$publicationVersion.get();
            if ((before & 1L) != 0L) {
                Thread.onSpinWait();
                continue;
            }
            long[] candidate = aerogel$entityTicking.toLongArray();
            long after = aerogel$publicationVersion.get();
            if (before == after && (after & 1L) == 0L) {
                snapshot = candidate;
                break;
            }
        }
        for (long key : snapshot) consumer.accept(key);
    }

    @Override
    public void aerogel$blockTickingListener(LongConsumer listener) {
        aerogel$blockTickingListener = java.util.Objects.requireNonNull(listener, "listener");
    }
}
