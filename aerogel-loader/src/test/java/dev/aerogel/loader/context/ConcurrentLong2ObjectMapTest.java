package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentLong2ObjectMapTest {
    @Test
    void fastIterableReflectsPublishedEntries() {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();
        map.put(7L, "visible");
        map.put(-11L, "hidden");
        map.remove(-11L);

        Map<Long, String> entries = new HashMap<>();
        Long2ObjectMaps.fastIterable(map).forEach(entry ->
            entries.put(entry.getLongKey(), entry.getValue()));

        assertEquals(Map.of(7L, "visible"), entries);
    }
    @Test
    void splitMixFinalizerIsReversibleForRepresentativeAndRandomKeys() {
        long[] representative = {
            0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
            pack(0, 0), pack(1, 0), pack(0, 1), pack(-1, 0), pack(0, -1),
            pack(Integer.MIN_VALUE, Integer.MAX_VALUE),
            pack(Integer.MAX_VALUE, Integer.MIN_VALUE)
        };
        for (long key : representative) {
            assertEquals(key, ConcurrentLong2ObjectMap.unspread(
                ConcurrentLong2ObjectMap.spread(key)));
        }

        SplittableRandom random = new SplittableRandom(0x5eed_c0de_26_02L);
        for (int index = 0; index < 100_000; index++) {
            long key = random.nextLong();
            assertEquals(key, ConcurrentLong2ObjectMap.unspread(
                ConcurrentLong2ObjectMap.spread(key)));
        }
    }

    @Test
    void mapExposesOriginalKeysAndPreservesPrimitiveMapSemantics() {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();
        long first = pack(12, -34);
        long second = pack(-56, 78);

        map.defaultReturnValue("missing");
        assertEquals("missing", map.get(first));
        assertNull(map.put(first, "first"));
        assertEquals("first", map.get(first));
        assertEquals("second", map.computeIfAbsent(second, key -> "second"));
        assertEquals("second", map.computeIfAbsent(second, key -> "replaced"));

        Set<Long> keys = new HashSet<>();
        map.keySet().forEach((long key) -> keys.add(key));
        assertEquals(Set.of(first, second), keys);
        assertEquals(Set.of("first", "second"), new HashSet<>(map.values()));
        assertEquals(2, map.size());

        assertEquals("first", map.remove(first));
        assertFalse(map.keySet().contains(first));
        assertTrue(map.keySet().contains(second));
        map.clear();
        assertEquals(0, map.size());
    }

    @Test
    void packedChunkKeysThatCollideUnderLongHashCodeAreSpreadApart() {
        long origin = pack(0, 0);
        long diagonal = pack(1, 1);

        assertEquals(Long.hashCode(origin), Long.hashCode(diagonal));
        assertFalse(Long.hashCode(ConcurrentLong2ObjectMap.spread(origin))
            == Long.hashCode(ConcurrentLong2ObjectMap.spread(diagonal)));
    }

    @Test
    void resurrectedEntrySurvivesWriteGenerationPublication() {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();
        map.put(1L, "first");
        assertEquals(1, map.size());

        assertEquals("first", map.remove(1L));
        map.put(2L, "second");
        map.put(1L, "resurrected");

        assertEquals(2, map.size());
        assertEquals("resurrected", map.get(1L));
        assertEquals("second", map.get(2L));
    }

    @Test
    void computeIfAbsentPublishesOneValueAcrossConcurrentReaders() throws Exception {
        ConcurrentLong2ObjectMap<String> map = new ConcurrentLong2ObjectMap<>();
        int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
        AtomicInteger mappings = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(workers)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return map.computeIfAbsent(42L, ignored -> {
                        mappings.incrementAndGet();
                        return "value";
                    });
                }));
            }
            start.countDown();
            for (var future : futures) {
                assertEquals("value", future.get(10, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, mappings.get());
        assertEquals("value", map.get(42L));
    }

    private static long pack(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }
}
