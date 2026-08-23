package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentLongSortedSetTest {
    @Test
    void rangeIterationUsesAStableSortedPrimitiveSnapshot() {
        ConcurrentLongSortedSet set = new ConcurrentLongSortedSet();
        set.add(9L);
        set.add(-2L);
        set.add(4L);
        set.add(12L);

        LongSortedSet range = set.subSet(0L, 10L);
        LongBidirectionalIterator iterator = range.iterator();
        set.add(6L);
        set.remove(4L);

        assertEquals(List.of(4L, 9L), collect(iterator));
        assertEquals(List.of(6L, 9L), collect(range.iterator()));
        assertEquals(2, range.size());
    }

    @Test
    void membershipMutationAndBackwardTraversalPreserveSetSemantics() {
        ConcurrentLongSortedSet set = new ConcurrentLongSortedSet();
        assertTrue(set.add(3L));
        assertFalse(set.add(3L));
        assertTrue(set.add(7L));
        assertTrue(set.contains(3L));
        assertTrue(set.remove(3L));
        assertFalse(set.remove(3L));

        LongBidirectionalIterator iterator = set.iterator();
        assertEquals(7L, iterator.nextLong());
        assertTrue(iterator.hasPrevious());
        assertEquals(7L, iterator.previousLong());
    }

    @Test
    void persistentAvlMatchesAReferenceSetAcrossMutations() {
        ConcurrentLongSortedSet actual = new ConcurrentLongSortedSet();
        TreeSet<Long> expected = new TreeSet<>();
        SplittableRandom random = new SplittableRandom(0xA3E0_26_02L);
        for (int operation = 0; operation < 20_000; operation++) {
            long value = random.nextLong(-2_000, 2_000);
            if (random.nextBoolean()) assertEquals(expected.add(value), actual.add(value));
            else assertEquals(expected.remove(value), actual.remove(value));
            if ((operation & 255) == 0) {
                assertEquals(new ArrayList<>(expected), collect(actual.iterator()));
            }
        }
        assertEquals(new ArrayList<>(expected), collect(actual.iterator()));
    }

    private static List<Long> collect(LongBidirectionalIterator iterator) {
        List<Long> values = new ArrayList<>();
        while (iterator.hasNext()) values.add(iterator.nextLong());
        return values;
    }
}
