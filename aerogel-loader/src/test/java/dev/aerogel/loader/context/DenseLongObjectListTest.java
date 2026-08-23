package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DenseLongObjectListTest {
    @Test
    void removalSwapsLastEntryWithoutLosingItsIndex() {
        DenseLongObjectList<String> values = new DenseLongObjectList<>();
        values.put(10L, "ten");
        values.put(20L, "twenty");
        values.put(30L, "thirty");

        assertTrue(values.remove(20L));
        assertFalse(values.containsKey(20L));
        assertTrue(values.containsKey(30L));
        values.put(30L, "updated");

        Set<String> snapshot = new HashSet<>();
        values.forEach(snapshot::add);
        assertEquals(Set.of("ten", "updated"), snapshot);
        assertEquals(2, values.size());
    }

    @Test
    void replacingAKeyDoesNotDuplicateIt() {
        DenseLongObjectList<String> values = new DenseLongObjectList<>();
        values.put(7L, "first");
        values.put(7L, "second");

        Set<String> snapshot = new HashSet<>();
        values.forEach(snapshot::add);
        assertEquals(Set.of("second"), snapshot);
        assertEquals(1, values.size());
    }
}
