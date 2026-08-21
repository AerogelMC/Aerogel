package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.PaddedAtomicLong;
import dev.aerogel.loader.internal.SimulationChunkTrackerBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.server.level.ChunkLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.server.level.SimulationChunkTracker")
abstract class SimulationChunkTrackerMixin implements SimulationChunkTrackerBridge {
    @Unique private final ConcurrentHashMap<Long, Boolean> aerogel$entityTicking =
        new ConcurrentHashMap<>();
    @Unique private final PaddedAtomicLong aerogel$publicationVersion =
        new PaddedAtomicLong();

    @Inject(method = "setLevel(JI)V", at = @At("HEAD"))
    private void aerogel$publishLevel(long key, int level, CallbackInfo callback) {
        aerogel$publicationVersion.incrementAndGet();
        if (ChunkLevel.isEntityTicking(level)) {
            aerogel$entityTicking.put(key, Boolean.TRUE);
        } else {
            aerogel$entityTicking.remove(key);
        }
        aerogel$publicationVersion.incrementAndGet();
    }

    @Override
    public void aerogel$forEachEntityTickingChunk(LongConsumer consumer) {
        Long[] snapshot;
        while (true) {
            long before = aerogel$publicationVersion.get();
            if ((before & 1L) != 0L) {
                Thread.onSpinWait();
                continue;
            }
            Long[] candidate = aerogel$entityTicking.keySet().toArray(Long[]::new);
            long after = aerogel$publicationVersion.get();
            if (before == after && (after & 1L) == 0L) {
                snapshot = candidate;
                break;
            }
        }
        for (Long key : snapshot) consumer.accept(key.longValue());
    }
}
