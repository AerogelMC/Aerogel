package dev.aerogel.loader.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionWorkersTest {
    @Test
    void availableWorkerClaimsInteractiveBeforeQueuedBulk() throws Exception {
        String property = "aerogel.network.compression.workers";
        String previous = System.getProperty(property);
        CompressionWorkers.shutdown();
        System.setProperty(property, "1");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(3);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        try {
            CompressionWorkers.execute(PacketPriority.BULK, () -> {
                order.add(1);
                firstStarted.countDown();
                await(releaseFirst);
                completed.countDown();
            });
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            CompressionWorkers.execute(PacketPriority.BULK, () -> {
                order.add(2);
                completed.countDown();
            });
            CompressionWorkers.execute(PacketPriority.INTERACTIVE, () -> {
                order.add(3);
                completed.countDown();
            });

            releaseFirst.countDown();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(1, 3, 2), order);
        } finally {
            releaseFirst.countDown();
            CompressionWorkers.shutdown();
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for worker test");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
