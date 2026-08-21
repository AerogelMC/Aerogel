package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentLong2ObjectMapTest {
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

    private static long pack(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }
}
