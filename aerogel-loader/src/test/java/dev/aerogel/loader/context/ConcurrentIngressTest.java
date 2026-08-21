package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentIngressTest {
    @Test
    void concurrentProducersDrainWithoutLossOrLimit() throws Exception {
        ConcurrentIngress<Integer> ingress = new ConcurrentIngress<>();
        int producers = 8;
        int perProducer = 10_000;
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[producers];
        for (int producer = 0; producer < producers; producer++) {
            int base = producer * perProducer;
            threads[producer] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                for (int index = 0; index < perProducer; index++) {
                    ingress.offer(base + index);
                }
            });
            threads[producer].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();

        Set<Integer> drained = new HashSet<>();
        ingress.drain(drained::add);
        assertEquals(producers * perProducer, drained.size());
        assertTrue(ingress.isEmpty());
    }
}
