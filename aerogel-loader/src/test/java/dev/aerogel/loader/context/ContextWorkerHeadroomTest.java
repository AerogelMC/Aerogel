package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextWorkerHeadroomTest {
    @Test
    void reportsLiveCapacityWhilePreservingOneProgressSlot() throws Exception {
        ContextServiceImpl contexts = new ContextServiceImpl(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable occupyWorker = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            assertEquals(2, contexts.availableWorkerCount());
            contexts.dispatch(occupyWorker);
            contexts.dispatch(occupyWorker);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(1, contexts.availableWorkerCount());
        } finally {
            release.countDown();
            contexts.close();
        }
    }
}
