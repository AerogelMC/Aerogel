package dev.aerogel.loader.context;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

/** Atomic, rollback-capable reservation of vanilla's per-player local mob cap. */
public final class LocalMobCapReservation {
    private static final ContextWorkerLocal<Capture> CAPTURE = ContextWorkerLocal.create();

    private final List<AtomicIntegerArray> counts;
    private final int category;

    private LocalMobCapReservation(List<AtomicIntegerArray> counts, int category) {
        this.counts = counts;
        this.category = category;
    }

    static LocalMobCapReservation acquire(
        LocalMobCapCalculator calculator, ChunkPos chunk, MobCategory category
    ) {
        Capture capture = new Capture(category);
        if (CAPTURE.get() != null) {
            throw new IllegalStateException("Nested local mob-cap reservation");
        }
        CAPTURE.set(capture);
        try {
            calculator.addMob(chunk, category);
        } catch (Throwable failure) {
            capture.release();
            throw failure;
        } finally {
            CAPTURE.remove();
        }
        if (!capture.allowed) {
            capture.release();
            return null;
        }
        return new LocalMobCapReservation(List.copyOf(capture.counts), category.ordinal());
    }

    /** Called by the MobCounts mixin instead of its ordinary increment. */
    public static boolean captureIncrement(
        AtomicIntegerArray counts, MobCategory category
    ) {
        Capture capture = CAPTURE.get();
        if (capture == null) return false;
        int ordinal = category.ordinal();
        int previous = counts.getAndIncrement(ordinal);
        capture.counts.add(counts);
        capture.allowed |= previous < category.getMaxInstancesPerChunk();
        return true;
    }

    void release() {
        for (AtomicIntegerArray count : counts) count.decrementAndGet(category);
    }

    private static final class Capture {
        private final MobCategory category;
        private final List<AtomicIntegerArray> counts = new ArrayList<>();
        private boolean allowed;

        private Capture(MobCategory category) {
            this.category = category;
        }

        private void release() {
            int ordinal = category.ordinal();
            for (AtomicIntegerArray count : counts) count.decrementAndGet(ordinal);
        }
    }
}
