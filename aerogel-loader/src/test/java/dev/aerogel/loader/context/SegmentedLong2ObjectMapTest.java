package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SegmentedLong2ObjectMapTest {
    @Test
    void clonePreservesAnIndependentLightSnapshot() {
        Long2ObjectOpenHashMap<String> original = new Long2ObjectOpenHashMap<>();
        original.put(1L, "one");
        SegmentedLong2ObjectMap<String> updating =
            new SegmentedLong2ObjectMap<>(original);

        SegmentedLong2ObjectMap<String> visible = updating.clone();
        updating.put(2L, "two");
        updating.remove(1L);

        assertEquals("one", visible.get(1L));
        assertNull(visible.get(2L));
        assertFalse(updating.containsKey(1L));
        assertEquals("two", updating.get(2L));
        assertNotSame(updating, visible);
    }

    @Test
    void repeatedSnapshotsOnlyCopyMutatedSegmentsAndRemainIndependent() {
        Long2ObjectOpenHashMap<String> original = new Long2ObjectOpenHashMap<>();
        original.put(1L, "one");
        SegmentedLong2ObjectMap<String> updating =
            new SegmentedLong2ObjectMap<>(original);

        SegmentedLong2ObjectMap<String> first = updating.clone();
        updating.put(1L, "updated");
        SegmentedLong2ObjectMap<String> second = updating.clone();
        updating.put(2L, "two");

        assertEquals("one", first.get(1L));
        assertNull(first.get(2L));
        assertEquals("updated", second.get(1L));
        assertNull(second.get(2L));
        assertEquals("updated", updating.get(1L));
        assertEquals("two", updating.get(2L));

        first.put(3L, "snapshot-write");
        assertFalse(second.containsKey(3L));
        assertFalse(updating.containsKey(3L));
    }

    @Test
    void growthSplitsBeforeAnySegmentCrossesTheGcDerivedLimit() {
        int limit = SegmentedLong2ObjectMap.maximumEntriesPerSegment();
        if (limit == Integer.MAX_VALUE) return;
        SegmentedLong2ObjectMap<Long> map =
            new SegmentedLong2ObjectMap<>(new Long2ObjectOpenHashMap<>());

        for (long key = 0; key <= limit; key++) map.put(key, key);

        assertTrue(map.segmentCount() > 1);
        assertEquals(limit + 1, map.size());
    }
}
