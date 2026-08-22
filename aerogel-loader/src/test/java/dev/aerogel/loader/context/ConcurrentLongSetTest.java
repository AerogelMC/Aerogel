package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentLongSetTest {
    @Test
    void parallelChunkOwnersCanPublishDistinctKeys() throws Exception {
        ConcurrentLongSet set = new ConcurrentLongSet();
        int workers = 16;
        int keysPerWorker = 4_096;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            int owner = worker;
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                long base = (long) owner * keysPerWorker;
                for (int key = 0; key < keysPerWorker; key++) {
                    assertTrue(set.add(base + key));
                }
            }));
        }
        start.countDown();
        for (Thread thread : threads) thread.join();

        assertEquals(workers * keysPerWorker, set.size());
        for (long key = 0; key < (long) workers * keysPerWorker; key++) {
            assertTrue(set.contains(key));
        }
    }

    @Test
    void addAndRemoveOfSameChunkRemainLinearizable() throws Exception {
        ConcurrentLongSet set = new ConcurrentLongSet();
        int workers = 16;
        int repetitions = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    set.add(42L);
                    set.remove(42L);
                }
            }));
        }
        start.countDown();
        for (Thread thread : threads) thread.join();

        set.add(42L);
        assertTrue(set.contains(42L));
        assertTrue(set.remove(42L));
        assertFalse(set.contains(42L));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
