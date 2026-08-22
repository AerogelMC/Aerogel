package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LatestTickTaskLaneTest {
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
