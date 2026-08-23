package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentLongSetTest {
    @Test
    void iteratorRestoresEveryExternallyVisibleKey() {
        ConcurrentLongSet set = new ConcurrentLongSet();
        long[] keys = {
            0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
            WorldContextImpl.key(17, 17), WorldContextImpl.key(-17, -17)
        };
        for (long key : keys) set.add(key);

        var observed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        var iterator = set.iterator();
        while (iterator.hasNext()) observed.add(iterator.nextLong());

        assertEquals(keys.length, observed.size());
        for (long key : keys) assertTrue(observed.contains(key));
    }

    @Test
    void primitiveArrayMethodsAreImplementedByTheConcreteClass() throws Exception {
        assertEquals(
            ConcurrentLongSet.class,
            ConcurrentLongSet.class.getMethod("toLongArray").getDeclaringClass()
        );
        assertEquals(
            ConcurrentLongSet.class,
            ConcurrentLongSet.class.getMethod("toArray", long[].class).getDeclaringClass()
        );

        ConcurrentLongSet set = new ConcurrentLongSet();
        set.add(7L);
        assertArrayEquals(new long[] {7L}, set.toLongArray());

        long[] target = {Long.MIN_VALUE, Long.MAX_VALUE};
        assertEquals(target, set.toArray(target));
        assertEquals(7L, target[0]);
        assertEquals(Long.MAX_VALUE, target[1]);
    }

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
