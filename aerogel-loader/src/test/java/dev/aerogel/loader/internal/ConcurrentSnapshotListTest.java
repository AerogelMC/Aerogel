package dev.aerogel.loader.internal;

import org.junit.jupiter.api.Test;
import java.util.Iterator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConcurrentSnapshotListTest {
    @Test
    void iteratorRetainsOneGenerationAcrossConcurrentStyleMutation() {
        ConcurrentSnapshotList<String> players = new ConcurrentSnapshotList<>(List.of("a", "b"));
        Iterator<String> broadcast = players.iterator();
        players.remove("a");
        players.add("c");

        assertEquals(List.of("a", "b"), collect(broadcast));
        assertEquals(List.of("b", "c"), List.copyOf(players));
    }

    private static <T> List<T> collect(Iterator<T> iterator) {
        java.util.ArrayList<T> values = new java.util.ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }
}
