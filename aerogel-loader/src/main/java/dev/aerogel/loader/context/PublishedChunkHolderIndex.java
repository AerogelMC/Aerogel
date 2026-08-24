package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ChunkHolder;

/**
 * Lock-free read index for generation workers.
 *
 * <p>The server owner publishes one immutable map per touched CPU-derived
 * stripe after an exact distance generation commits. Readers therefore see a
 * complete old or new stripe and never touch ChunkMap's mutable fastutil map.</p>
 */
public final class PublishedChunkHolderIndex {
    private final PaddedAtomicReference<Long2ObjectOpenHashMap<ChunkHolder>>[] stripes;
    private final int mask;

    @SuppressWarnings("unchecked")
    public PublishedChunkHolderIndex() {
        int count = Integer.highestOneBit(
            Math.max(1, Runtime.getRuntime().availableProcessors()));
        stripes = (PaddedAtomicReference<Long2ObjectOpenHashMap<ChunkHolder>>[])
            new PaddedAtomicReference<?>[count];
        for (int index = 0; index < count; index++) {
            stripes[index] = new PaddedAtomicReference<>(new Long2ObjectOpenHashMap<>());
        }
        mask = count - 1;
    }

    public ChunkHolder get(long chunkKey) {
        return stripes[owner(chunkKey)].get().get(chunkKey);
    }

    /** Publishes every holder transition in one immutable distance generation. */
    @SuppressWarnings("unchecked")
    public void publish(
        ExactChunkDistanceGraph.ChangeBatch changes, HolderResolver resolver
    ) {
        LongArrayList[] keys = new LongArrayList[stripes.length];
        ObjectArrayList<ChunkHolder>[] holders =
            (ObjectArrayList<ChunkHolder>[]) new ObjectArrayList<?>[stripes.length];
        changes.publish((chunkKey, level) -> {
            int owner = owner(chunkKey);
            LongArrayList ownerKeys = keys[owner];
            ObjectArrayList<ChunkHolder> ownerHolders = holders[owner];
            if (ownerKeys == null) {
                ownerKeys = new LongArrayList();
                ownerHolders = new ObjectArrayList<>();
                keys[owner] = ownerKeys;
                holders[owner] = ownerHolders;
            }
            ownerKeys.add(chunkKey);
            ownerHolders.add(resolver.resolve(chunkKey, level));
        });

        for (int owner = 0; owner < stripes.length; owner++) {
            LongArrayList ownerKeys = keys[owner];
            if (ownerKeys == null) continue;
            Long2ObjectOpenHashMap<ChunkHolder> next = stripes[owner].get().clone();
            ObjectArrayList<ChunkHolder> ownerHolders = holders[owner];
            for (int index = 0; index < ownerKeys.size(); index++) {
                long chunkKey = ownerKeys.getLong(index);
                ChunkHolder holder = ownerHolders.get(index);
                if (holder == null) next.remove(chunkKey);
                else next.put(chunkKey, holder);
            }
            stripes[owner].set(next);
        }
    }

    private int owner(long key) {
        return (int) ConcurrentLong2ObjectMap.spread(key) & mask;
    }

    @FunctionalInterface
    public interface HolderResolver {
        ChunkHolder resolve(long chunkKey, int level);
    }
}
