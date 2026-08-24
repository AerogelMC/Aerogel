package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ExactChunkDistanceGraphTest {
    @Test
    void oneSourceProducesTheExactChebyshevField() {
        ExactChunkDistanceGraph graph = new ExactChunkDistanceGraph(6, 4);
        Long2IntOpenHashMap published = levels(5);
        graph.updateSource(key(2, -3), 1);
        graph.apply(ExactChunkDistanceGraphTest::sequential)
            .publish(published::put);

        for (int x = -4; x <= 8; x++) {
            for (int z = -9; z <= 3; z++) {
                int expected = Math.min(5,
                    1 + Math.max(Math.abs(x - 2), Math.abs(z + 3)));
                assertEquals(expected, published.get(key(x, z)), x + "," + z);
            }
        }
    }

    @Test
    void randomizedUpdatesMatchTheClosedFormMinimum() {
        int maximumLevel = 9;
        ExactChunkDistanceGraph graph = new ExactChunkDistanceGraph(
            maximumLevel + 1, 7);
        Long2IntOpenHashMap sources = levels(maximumLevel);
        Long2IntOpenHashMap published = levels(maximumLevel);
        Random random = new Random(0xA3E09E1L);

        try (ForkJoinPool pool = new ForkJoinPool(7)) {
            for (int wave = 0; wave < 300; wave++) {
                int changes = 1 + random.nextInt(6);
                for (int change = 0; change < changes; change++) {
                    int x = random.nextInt(15) - 7;
                    int z = random.nextInt(15) - 7;
                    long key = key(x, z);
                    int level = random.nextInt(maximumLevel + 5) - 2;
                    int clamped = Math.max(0, Math.min(maximumLevel, level));
                    graph.updateSource(key, level);
                    if (clamped == maximumLevel) sources.remove(key);
                    else sources.put(key, clamped);
                }
                graph.apply((tasks, task) -> parallel(pool, tasks, task))
                    .publish((key, level) -> {
                        if (level == maximumLevel) published.remove(key);
                        else published.put(key, level);
                    });

                for (int x = -18; x <= 18; x++) {
                    for (int z = -18; z <= 18; z++) {
                        assertEquals(bruteForce(sources, maximumLevel, x, z),
                            published.get(key(x, z)),
                            "wave=" + wave + " chunk=" + x + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void addingAndRemovingBeforePublicationHasNoVisibleTransition() {
        ExactChunkDistanceGraph graph = new ExactChunkDistanceGraph(8, 3);
        graph.updateSource(key(0, 0), 0);
        graph.updateSource(key(0, 0), Integer.MAX_VALUE);
        assertEquals(0, graph.apply(ExactChunkDistanceGraphTest::sequential)
            .publish((key, level) -> { throw new AssertionError("unexpected change"); }));
    }

    @Test
    void asynchronousProducerPublishesOnlyACompletedGenerationWithoutCallerJoin()
        throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            ExactChunkDistanceGraph graph = new ExactChunkDistanceGraph(
                6, 4,
                task -> executor.execute(() -> {
                    started.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("producer was not released");
                        }
                        task.run();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    } finally {
                        completed.countDown();
                    }
                }),
                ExactChunkDistanceGraphTest::sequential,
                Runnable::run);
            Long2IntOpenHashMap published = levels(5);

            graph.updateSource(key(0, 0), 0);
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertEquals(0, graph.publishCompleted(published::put));
            release.countDown();
            assertTrue(completed.await(10, TimeUnit.SECONDS));
            graph.publishCompleted(published::put);
            assertEquals(0, published.get(key(0, 0)));
            assertEquals(2, published.get(key(2, 1)));
        }
    }

    @Test
    void generationPublisherPreservesOrderAndWaitsForOwnerSettlement()
        throws Exception {
        CountDownLatch firstOffered = new CountDownLatch(1);
        CountDownLatch secondOffered = new CountDownLatch(1);
        CompletableFuture<Void> firstOwnersSettled = new CompletableFuture<>();
        AtomicInteger generations = new AtomicInteger();
        try (var executor = Executors.newSingleThreadExecutor()) {
            ExactChunkDistanceGraph graph = new ExactChunkDistanceGraph(
                6, 4, executor::execute, ExactChunkDistanceGraphTest::sequential,
                Runnable::run);
            graph.bindGenerationPublisher(changes -> {
                int generation = generations.incrementAndGet();
                if (generation == 1) {
                    firstOffered.countDown();
                    return firstOwnersSettled;
                }
                secondOffered.countDown();
                return CompletableFuture.completedFuture(null);
            });

            graph.updateSource(key(0, 0), 0);
            CompletableFuture<Void> firstPublished =
                graph.publicationAfterQueuedUpdates();
            assertTrue(firstOffered.await(2, TimeUnit.SECONDS));
            assertFalse(firstPublished.isDone());

            graph.updateSource(key(8, 0), 0);
            CompletableFuture<Void> secondPublished =
                graph.publicationAfterQueuedUpdates();
            assertFalse(secondOffered.await(100, TimeUnit.MILLISECONDS),
                "a later distance generation must not overtake unsettled owners");

            firstOwnersSettled.complete(null);
            assertTrue(secondOffered.await(2, TimeUnit.SECONDS));
            firstPublished.get(2, TimeUnit.SECONDS);
            secondPublished.get(2, TimeUnit.SECONDS);
            assertEquals(2, generations.get());
        }
    }

    private static int bruteForce(
        Long2IntOpenHashMap sources, int maximumLevel, int targetX, int targetZ
    ) {
        int result = maximumLevel;
        for (var source : sources.long2IntEntrySet()) {
            long key = source.getLongKey();
            int x = (int) key;
            int z = (int) (key >>> 32);
            result = Math.min(result, source.getIntValue()
                + Math.max(Math.abs(targetX - x), Math.abs(targetZ - z)));
        }
        return Math.min(result, maximumLevel);
    }

    private static Long2IntOpenHashMap levels(int defaultLevel) {
        Long2IntOpenHashMap levels = new Long2IntOpenHashMap();
        levels.defaultReturnValue(defaultLevel);
        return levels;
    }

    private static void sequential(int tasks, IntConsumer task) {
        for (int index = 0; index < tasks; index++) task.accept(index);
    }

    private static void parallel(ForkJoinPool pool, int tasks, IntConsumer task) {
        pool.invoke(new RangeAction(task, 0, tasks));
    }

    private static long key(int x, int z) {
        return ExactChunkDistanceGraph.pack(x, z);
    }

    @SuppressWarnings("serial")
    private static final class RangeAction extends RecursiveAction {
        private final IntConsumer task;
        private final int from;
        private final int to;

        private RangeAction(IntConsumer task, int from, int to) {
            this.task = task;
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute() {
            if (to - from == 1) {
                task.accept(from);
                return;
            }
            int middle = (from + to) >>> 1;
            invokeAll(new RangeAction(task, from, middle),
                new RangeAction(task, middle, to));
        }
    }
}
