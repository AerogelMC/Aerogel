package dev.aerogel.loader.context;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NaturalSpawnReservationTest {
    private static final MobCategory CATEGORY = MobCategory.MONSTER;
    private static final ChunkPos CHUNK = new ChunkPos(3, -7);

    @AfterEach
    void releaseThreadReservation() {
        NaturalSpawnReservation.releaseCurrent();
    }

    @Test
    void committedReservationConsumesExactlyOneGlobalAndLocalPermit() {
        AtomicIntegerArray global = counts();
        AtomicIntegerArray local = counts();
        TestLocalCaps localCaps = new TestLocalCaps(local);

        assertTrue(NaturalSpawnReservation.acquire(global, CATEGORY, 1, localCaps, CHUNK));
        assertEquals(1, global.get(CATEGORY.ordinal()));
        assertEquals(1, local.get(CATEGORY.ordinal()));
        assertTrue(NaturalSpawnReservation.commitGlobal(global, CATEGORY));
        assertTrue(NaturalSpawnReservation.commitLocal(localCaps, CHUNK, CATEGORY));

        assertFalse(NaturalSpawnReservation.acquire(global, CATEGORY, 1, localCaps, CHUNK));
        assertEquals(1, global.get(CATEGORY.ordinal()));
        assertEquals(1, local.get(CATEGORY.ordinal()));
    }

    @Test
    void failedAttemptRollsBackBothPermits() {
        AtomicIntegerArray global = counts();
        AtomicIntegerArray local = counts();
        TestLocalCaps localCaps = new TestLocalCaps(local);

        assertTrue(NaturalSpawnReservation.acquire(global, CATEGORY, 10, localCaps, CHUNK));
        NaturalSpawnReservation.releaseCurrent();

        assertEquals(0, global.get(CATEGORY.ordinal()));
        assertEquals(0, local.get(CATEGORY.ordinal()));
    }

    @Test
    void fullLocalCapRejectsAndRestoresGlobalReservation() {
        AtomicIntegerArray global = counts();
        AtomicIntegerArray local = counts();
        local.set(CATEGORY.ordinal(), CATEGORY.getMaxInstancesPerChunk());

        assertFalse(NaturalSpawnReservation.acquire(
            global, CATEGORY, 10, new TestLocalCaps(local), CHUNK));
        assertEquals(0, global.get(CATEGORY.ordinal()));
        assertEquals(CATEGORY.getMaxInstancesPerChunk(), local.get(CATEGORY.ordinal()));
    }

    @Test
    void anyNearbyPlayerWithRoomMatchesVanillaLocalCapSemantics() {
        AtomicIntegerArray full = counts();
        AtomicIntegerArray available = counts();
        full.set(CATEGORY.ordinal(), CATEGORY.getMaxInstancesPerChunk());
        TestLocalCaps localCaps = new TestLocalCaps(full, available);

        assertTrue(NaturalSpawnReservation.acquire(
            counts(), CATEGORY, 10, localCaps, CHUNK));
        assertEquals(CATEGORY.getMaxInstancesPerChunk() + 1, full.get(CATEGORY.ordinal()));
        assertEquals(1, available.get(CATEGORY.ordinal()));
    }

    private static AtomicIntegerArray counts() {
        return new AtomicIntegerArray(MobCategory.values().length);
    }

    private static final class TestLocalCaps extends LocalMobCapCalculator {
        private final List<AtomicIntegerArray> players;

        private TestLocalCaps(AtomicIntegerArray... players) {
            this.players = List.of(players);
        }

        @Override
        public void addMob(ChunkPos chunk, MobCategory category) {
            for (AtomicIntegerArray player : players) {
                if (!LocalMobCapReservation.captureIncrement(player, category)) {
                    player.incrementAndGet(category.ordinal());
                }
            }
        }
    }
}
