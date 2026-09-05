package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

final class ContextRegistrationIndexTest {
    @Test
    void legacyRemovalLosesPublicationIntoPreviouslySelectedBucket() throws Exception {
        var index = new ConcurrentHashMap<String, ConcurrentHashMap<Object, String>>();
        Object oldEntity = new Object();
        index.computeIfAbsent("chunk", ignored -> new ConcurrentHashMap<>()).put(oldEntity, "old");
        BlockingKey newEntity = new BlockingKey();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> index.computeIfAbsent("chunk",
                ignored -> new ConcurrentHashMap<>()).put(newEntity, "new"));
            try {
                assertTrue(newEntity.entered.await(5, TimeUnit.SECONDS));
                var entries = index.get("chunk");
                entries.remove(oldEntity, "old");
                if (entries.isEmpty()) index.remove("chunk", entries);
            } finally {
                newEntity.resume.countDown();
            }
            writer.get(5, TimeUnit.SECONDS);
            assertNull(index.get("chunk"), "Legacy publication is detached from tick enumeration");
        }
    }

    @Test
    void lastRemovalCannotDetachBucketWhileEntityPublicationIsInFlight() throws Exception {
        var index = new ContextRegistrationIndex<String, Object, String>();
        Object oldEntity = new Object();
        index.put("chunk", oldEntity, "old");
        BlockingKey newEntity = new BlockingKey();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> index.put("chunk", newEntity, "new"));
            try {
                assertTrue(newEntity.entered.await(5, TimeUnit.SECONDS));
                index.remove("chunk", oldEntity, "old");
            } finally {
                newEntity.resume.countDown();
            }
            writer.get(5, TimeUnit.SECONDS);
            assertNotNull(index.get("chunk"));
            assertEquals("new", index.get("chunk").get(newEntity));
            assertEquals(1, index.snapshot().size());
            index.remove("chunk", newEntity, "new");
            assertTrue(index.isEmpty(), "Empty owners must still be reclaimed");
        }
    }

    @Test
    void racingRetirementAndPublicationPreserveEverySurvivingRegistration() throws Exception {
        var index = new ContextRegistrationIndex<Integer, Integer, Object>();
        Object oldRegistration = new Object();
        Object newRegistration = new Object();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 3000; round++) {
                index.put(0, 0, oldRegistration);
                CountDownLatch start = new CountDownLatch(1);
                var remove = executor.submit(() -> {
                    await(start);
                    index.remove(0, 0, oldRegistration);
                });
                var put = executor.submit(() -> {
                    await(start);
                    index.put(0, 1, newRegistration);
                });
                start.countDown();
                remove.get(5, TimeUnit.SECONDS);
                put.get(5, TimeUnit.SECONDS);
                assertNotNull(index.get(0), "Lost owner at round " + round);
                assertSame(newRegistration, index.get(0).get(1));
                index.remove(0, 1, newRegistration);
                assertTrue(index.isEmpty());
            }
        }
    }

    @Test
    void staleRemovalDoesNotDeleteReplacementOrAnotherOwner() {
        var index = new ContextRegistrationIndex<String, Integer, Object>();
        Object oldRegistration = new Object();
        Object replacement = new Object();
        index.put("first", 1, oldRegistration);
        index.put("first", 1, replacement);
        index.put("second", 1, oldRegistration);
        index.remove("first", 1, oldRegistration);
        assertSame(replacement, index.get("first").get(1));
        index.remove("first", 1, replacement);
        assertNull(index.get("first"));
        assertSame(oldRegistration, index.get("second").get(1));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test handoff timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class BlockingKey {
        private final AtomicBoolean first = new AtomicBoolean(true);
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        @Override public int hashCode() {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                await(resume);
            }
            return 17;
        }
    }
}
