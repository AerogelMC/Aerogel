package dev.aerogel.loader.context;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;

import java.util.concurrent.atomic.AtomicIntegerArray;

/** One exact global/local mob-cap permit for the current natural-spawn attempt. */
public final class NaturalSpawnReservation {
    private static final ThreadLocal<Reservation> CURRENT = new ThreadLocal<>();

    private NaturalSpawnReservation() { }

    public static boolean acquire(
        AtomicIntegerArray globalCounts,
        MobCategory category,
        int globalLimit,
        LocalMobCapCalculator localCaps,
        ChunkPos chunk
    ) {
        releaseCurrent();
        int ordinal = category.ordinal();
        int count = globalCounts.get(ordinal);
        while (count < globalLimit) {
            if (globalCounts.compareAndSet(ordinal, count, count + 1)) {
                LocalMobCapReservation local = LocalMobCapReservation.acquire(
                    localCaps, chunk, category);
                if (local == null) {
                    globalCounts.decrementAndGet(ordinal);
                    return false;
                }
                CURRENT.set(new Reservation(
                    globalCounts, category, localCaps, chunk, local, count));
                return true;
            }
            count = globalCounts.get(ordinal);
        }
        return false;
    }

    public static boolean commitGlobal(
        AtomicIntegerArray globalCounts, MobCategory category
    ) {
        Reservation reservation = CURRENT.get();
        if (reservation == null
            || reservation.globalCounts != globalCounts
            || reservation.category != category) {
            releaseCurrent();
            return false;
        }
        reservation.globalCommitted = true;
        return true;
    }

    public static boolean commitLocal(
        LocalMobCapCalculator localCaps, ChunkPos chunk, MobCategory category
    ) {
        Reservation reservation = CURRENT.get();
        if (reservation == null || !reservation.globalCommitted
            || reservation.category != category
            || reservation.localCaps != localCaps
            || !reservation.chunk.equals(chunk)) {
            releaseCurrent();
            return false;
        }
        CURRENT.remove();
        return true;
    }

    public static int reservedPreviousCount() {
        Reservation reservation = CURRENT.get();
        return reservation == null ? 0 : reservation.previousGlobalCount;
    }

    public static void releaseCurrent() {
        Reservation reservation = CURRENT.get();
        if (reservation == null) return;
        CURRENT.remove();
        reservation.globalCounts.decrementAndGet(reservation.category.ordinal());
        reservation.local.release();
    }

    private static final class Reservation {
        private final AtomicIntegerArray globalCounts;
        private final MobCategory category;
        private final LocalMobCapCalculator localCaps;
        private final ChunkPos chunk;
        private final LocalMobCapReservation local;
        private final int previousGlobalCount;
        private boolean globalCommitted;

        private Reservation(
            AtomicIntegerArray globalCounts,
            MobCategory category,
            LocalMobCapCalculator localCaps,
            ChunkPos chunk,
            LocalMobCapReservation local,
            int previousGlobalCount
        ) {
            this.globalCounts = globalCounts;
            this.category = category;
            this.localCaps = localCaps;
            this.chunk = chunk;
            this.local = local;
            this.previousGlobalCount = previousGlobalCount;
        }
    }
}
