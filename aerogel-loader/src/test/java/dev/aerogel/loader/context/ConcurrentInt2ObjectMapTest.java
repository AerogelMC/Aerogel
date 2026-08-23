package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentInt2ObjectMapTest {
    @Test
    void unchangedEnumerationReusesOneImmutableImage() {
        ConcurrentInt2ObjectMap<String> map = new ConcurrentInt2ObjectMap<>();
        map.put(1, "one");
        map.put(2, "two");

        var first = map.values();
        assertSame(first, map.values());
        assertEquals(2, first.size());
        assertThrows(UnsupportedOperationException.class,
            () -> first.add("three"));
        assertThrows(UnsupportedOperationException.class,
            () -> first.iterator().remove());

        map.put(3, "three");
        var second = map.values();
        assertNotSame(first, second);
        assertEquals(2, first.size());
        assertEquals(3, second.size());
        assertSame(second, map.values());
    }

    @Test
    void concurrentWritersPublishCompleteStableImages() throws Exception {
        ConcurrentInt2ObjectMap<Integer> map = new ConcurrentInt2ObjectMap<>();
        int workers = 8;
        int entriesPerWorker = 2_048;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            int owner = worker;
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                int base = owner * entriesPerWorker;
                for (int index = 0; index < entriesPerWorker; index++) {
                    map.put(base + index, base + index);
                }
            }));
        }
        start.countDown();
        while (threads.stream().anyMatch(Thread::isAlive)) {
            for (Integer value : map.values()) assertTrue(value >= 0);
        }
        for (Thread thread : threads) thread.join();

        var published = map.values();
        assertEquals(workers * entriesPerWorker, published.size());
        assertSame(published, map.values());
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
