package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConcurrentAppendListTest {
    @Test
    void concurrentAppendsAreLossless() throws Exception {
        ConcurrentAppendList<Integer> list = new ConcurrentAppendList<>();
        int threads = 8;
        int perThread = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        Thread[] writers = new Thread[threads];
        for (int thread = 0; thread < threads; thread++) {
            int base = thread * perThread;
            writers[thread] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                for (int index = 0; index < perThread; index++) list.add(base + index);
            });
            writers[thread].start();
        }
        start.countDown();
        for (Thread writer : writers) writer.join();

        Set<Integer> observed = new HashSet<>();
        list.forEach(observed::add);
        assertEquals(threads * perThread, list.size());
        assertEquals(threads * perThread, observed.size());
    }
}
