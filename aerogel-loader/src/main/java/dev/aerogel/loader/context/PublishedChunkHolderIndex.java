package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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

    /** Publishes holders captured by the control-plane owner in one generation. */
    @SuppressWarnings("unchecked")
    public void publish(long[] chunkKeys, ChunkHolder[] publishedHolders) {
        if (chunkKeys.length != publishedHolders.length) {
            throw new IllegalArgumentException("holder publication arrays differ in length");
        }
        LongArrayList[] keys = new LongArrayList[stripes.length];
        java.util.ArrayList<ChunkHolder>[] holders =
            (java.util.ArrayList<ChunkHolder>[]) new java.util.ArrayList<?>[stripes.length];
        for (int index = 0; index < chunkKeys.length; index++) {
            long chunkKey = chunkKeys[index];
            int owner = owner(chunkKey);
            LongArrayList ownerKeys = keys[owner];
            java.util.ArrayList<ChunkHolder> ownerHolders = holders[owner];
            if (ownerKeys == null) {
                ownerKeys = new LongArrayList();
                ownerHolders = new java.util.ArrayList<>();
                keys[owner] = ownerKeys;
                holders[owner] = ownerHolders;
            }
            ownerKeys.add(chunkKey);
            ownerHolders.add(publishedHolders[index]);
        }

        for (int owner = 0; owner < stripes.length; owner++) {
            LongArrayList ownerKeys = keys[owner];
            if (ownerKeys == null) continue;
            Long2ObjectOpenHashMap<ChunkHolder> next = stripes[owner].get().clone();
            java.util.ArrayList<ChunkHolder> ownerHolders = holders[owner];
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

}
