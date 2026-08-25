package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LatestTickTaskLaneTest {
    @Test
    void producerStartsWhileContextComputationPoolIsOccupied() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            LatestTickTaskLane lane = new LatestTickTaskLane(scheduler, "test");
            CountDownLatch computationStarted = new CountDownLatch(1);
            CountDownLatch releaseComputation = new CountDownLatch(1);
            CountDownLatch producerStarted = new CountDownLatch(1);

            assertTrue(scheduler.executeComputation(() -> {
                computationStarted.countDown();
                try {
                    assertTrue(releaseComputation.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(computationStarted.await(1, TimeUnit.SECONDS));

            NativeTickToken token = new NativeTickToken(1L);
            lane.offer(token, producerStarted::countDown);
            token.seal();

            assertTrue(producerStarted.await(1, TimeUnit.SECONDS),
                "producer admission must not wait in the Context computation pool");
            releaseComputation.countDown();
            lane.close();
        }
    }

    @Test
    void overloadRunsCurrentThenLatestProducerOnly() throws Exception {
        try (ContextServiceImpl scheduler = new ContextServiceImpl(1)) {
            LatestTickTaskLane lane = new LatestTickTaskLane(scheduler);
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch latestFinished = new CountDownLatch(1);
            AtomicInteger runs = new AtomicInteger();

            NativeTickToken first = new NativeTickToken(1L);
            lane.offer(first, () -> {
                runs.incrementAndGet();
                firstStarted.countDown();
                try {
                    assertTrue(releaseFirst.await(1, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            });
            first.seal();
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            for (long tick = 2L; tick <= 1_000L; tick++) {
                NativeTickToken token = new NativeTickToken(tick);
                long requestedTick = tick;
                lane.offer(token, () -> {
                    runs.incrementAndGet();
                    if (requestedTick == 1_000L) latestFinished.countDown();
                });
                token.seal();
            }

            releaseFirst.countDown();
            assertTrue(latestFinished.await(1, TimeUnit.SECONDS));
            assertEquals(2, runs.get());
            lane.close();
        }
    }
}
