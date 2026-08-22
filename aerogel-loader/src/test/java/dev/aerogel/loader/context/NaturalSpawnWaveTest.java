package dev.aerogel.loader.context;

import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NaturalSpawnWaveTest {
    @Test
    void nextWaveWaitsForEveryChunkFromPreviousWave() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            NaturalSpawnWave first = world.beginNaturalSpawnWave();
            assertTrue(first.register());
            NaturalSpawnWave second = world.beginNaturalSpawnWave();
            CountDownLatch secondStarted = new CountDownLatch(1);
            second.afterPredecessor(secondStarted::countDown);

            first.preparationComplete();
            first.seal();
            assertFalse(secondStarted.await(50, TimeUnit.MILLISECONDS));

            first.taskComplete();
            assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void preparedContinuationCanHandOffToChunkAfterWaveWasSealed() {
        NaturalSpawnWave wave = new NaturalSpawnWave(
            java.util.concurrent.CompletableFuture.completedFuture(null),
            new java.util.concurrent.CompletableFuture<>());
        assertTrue(wave.register()); // prepared continuation
        wave.preparationComplete();
        wave.seal();

        assertTrue(wave.register()); // chunk task registered by that continuation
        wave.taskComplete();         // continuation finishes
        assertFalse(wave.completion().isDone());

        wave.taskComplete();
        assertTrue(wave.completion().isDone());
    }

    @Test
    void rejectedChunkWorkReleasesItsWaveRegistration() {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            ChunkContextImpl context = world.context(0, 0);
            NativeChunkLane lane = context.chunkLane();
            NaturalSpawnWave wave = world.beginNaturalSpawnWave();
            assertTrue(wave.register());
            context.deactivate();

            lane.offer(new LevelChunk(), ignored -> { }, wave::taskComplete);
            wave.preparationComplete();
            wave.seal();

            assertTrue(wave.completion().isDone());
        }
    }

    @Test
    void waveWaitsUntilSpawnedEntityIndexesArePublished() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            WorldContextImpl world = new WorldContextImpl(scheduler, null);
            NativeChunkLane lane = world.context(0, 0).chunkLane();
            NaturalSpawnWave wave = world.beginNaturalSpawnWave();
            assertTrue(wave.register());
            CountDownLatch actionRan = new CountDownLatch(1);

            lane.offer(new LevelChunk(), ignored -> {
                NativeTickCoordinator.deferGlobalCommit(() -> { });
                actionRan.countDown();
            }, wave::taskComplete);
            wave.preparationComplete();
            wave.seal();

            assertTrue(actionRan.await(1, TimeUnit.SECONDS));
            assertFalse(wave.completion().isDone());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (!wave.completion().isDone() && System.nanoTime() < deadline) {
                NativeTickCoordinator.pumpMainThread();
                Thread.onSpinWait();
            }
            assertTrue(wave.completion().isDone());
        }
    }
}
