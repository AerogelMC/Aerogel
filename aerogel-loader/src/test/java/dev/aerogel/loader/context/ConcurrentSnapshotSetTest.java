package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentSnapshotSetTest {
    @Test
    void traversalImageChangesExactlyWhenMembershipChanges() {
        ConcurrentSnapshotSet<Integer> values = new ConcurrentSnapshotSet<>();
        values.add(1);
        values.add(2);
        assertEquals(Set.of(1, 2), snapshot(values));

        assertFalse(values.add(2));
        assertEquals(Set.of(1, 2), snapshot(values));

        assertTrue(values.remove(1));
        values.add(3);
        assertEquals(Set.of(2, 3), snapshot(values));
    }

    @Test
    void iteratorRemovalUpdatesMembershipAndNextSnapshot() {
        ConcurrentSnapshotSet<Integer> values = new ConcurrentSnapshotSet<>();
        values.add(1);
        values.add(2);
        Iterator<Integer> iterator = values.iterator();
        Integer removed = iterator.next();
        iterator.remove();

        assertFalse(values.contains(removed));
        assertEquals(1, values.size());
        assertEquals(new HashSet<>(values), snapshot(values));
    }

    private static <E> Set<E> snapshot(Set<E> values) {
        return new HashSet<>(values);
    }
}
