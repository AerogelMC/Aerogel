package dev.aerogel.loader.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentSnapshotMapTest {
    @Test
    void iteratorRetainsOneGenerationAcrossMutation() {
        ConcurrentSnapshotMap<String, Integer> map =
            new ConcurrentSnapshotMap<>(Map.of("a", 1, "b", 2));
        Iterator<Map.Entry<String, Integer>> snapshot = map.entrySet().iterator();

        map.remove("a");
        map.put("c", 3);

        Map<String, Integer> observed = collect(snapshot);
        assertEquals(Map.of("a", 1, "b", 2), observed);
        assertEquals(Map.of("b", 2, "c", 3), Map.copyOf(map));
    }

    @Test
    void concurrentFirstPublicationKeepsOneValueAndEveryKey() throws Exception {
        ConcurrentSnapshotMap<Integer, Integer> map = new ConcurrentSnapshotMap<>(Map.of());
        int workers = 8;
        int keys = 256;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int value = worker;
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int key = 0; key < keys; key++) {
                        int published = map.computeIfAbsent(key, ignored -> value);
                        assertTrue(published >= 0 && published < workers);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
        }

        assertEquals(keys, map.size());
        assertTrue(map.values().stream().allMatch(value -> value >= 0 && value < workers));
    }

    private static <K, V> Map<K, V> collect(Iterator<Map.Entry<K, V>> iterator) {
        java.util.HashMap<K, V> values = new java.util.HashMap<>();
        iterator.forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue()));
        return values;
    }
}
