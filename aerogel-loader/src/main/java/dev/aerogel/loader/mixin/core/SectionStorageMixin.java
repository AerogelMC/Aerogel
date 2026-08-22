package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.minecraft.world.level.chunk.storage.SectionStorage")
abstract class SectionStorageMixin<R> {
    @Shadow @Final @Mutable private Long2ObjectMap<Optional<R>> storage;
    @Shadow protected abstract Optional<R> get(long sectionKey);
    @Shadow protected abstract boolean outsideStoredRange(long sectionKey);
    @Invoker("unpackChunk") protected abstract void aerogel$unpackChunk(ChunkPos chunk);

    @Unique private final ConcurrentHashMap<Long, CompletableFuture<Void>>
        aerogel$chunkLoads = new ConcurrentHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$publishSectionsConcurrently(CallbackInfo callback) {
        storage = new ConcurrentLong2ObjectMap<>();
    }

    /**
     * @author Aerogel
     * @reason Preserve vanilla semantics without allocating a cancellable Mixin
     * callback on every villager POI lookup.
     */
    @Overwrite
    protected Optional<R> getOrLoad(long sectionKey) {
        if (outsideStoredRange(sectionKey)) {
            return Optional.empty();
        }
        Optional<R> loaded = get(sectionKey);
        if (loaded != null) {
            return loaded;
        }

        ChunkPos chunk = SectionPos.of(sectionKey).chunk();
        long chunkKey = chunk.pack();
        CompletableFuture<Void> mine = new CompletableFuture<>();
        CompletableFuture<Void> existing = aerogel$chunkLoads.putIfAbsent(chunkKey, mine);
        if (existing == null) {
            try {
                aerogel$unpackChunk(chunk);
                mine.complete(null);
            } catch (Throwable error) {
                mine.completeExceptionally(error);
                throw error;
            } finally {
                aerogel$chunkLoads.remove(chunkKey, mine);
            }
        } else {
            existing.join();
        }

        loaded = get(sectionKey);
        if (loaded == null) {
            throw new IllegalStateException("POI section load completed without publication");
        }
        return loaded;
    }
}
