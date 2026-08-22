package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PublishedLong2ObjectMapTest {
    @Test
    void publishesExactWritesRemovalsAndDefaultValue() {
        PublishedLong2ObjectMap<String> map = new PublishedLong2ObjectMap<>();
        map.defaultReturnValue("hidden");

        assertEquals("hidden", map.get(4L));
        map.put(4L, "ticking");
        assertEquals("ticking", map.get(4L));
        map.put(4L, "tracked");
        assertEquals("tracked", map.get(4L));
        map.remove(4L);
        assertEquals("hidden", map.get(4L));
    }

    @Test
    void readerCannotRetainAnImageAcrossACompletedWrite() throws Exception {
        PublishedLong2ObjectMap<String> map = new PublishedLong2ObjectMap<>();
        map.put(9L, "before");
        assertEquals("before", map.get(9L));

        CountDownLatch written = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            map.put(9L, "after");
            written.countDown();
        });
        writer.start();
        written.await();
        writer.join();

        assertEquals("after", map.get(9L));
    }

    @Test
    void fastIterablePublishesLoadedEntriesAndOmitsRemovedEntries() {
        PublishedLong2ObjectMap<String> map = new PublishedLong2ObjectMap<>();
        map.defaultReturnValue("fresh");
        map.put(4L, "loaded");
        map.put(-9L, "pending");
        map.remove(-9L);

        Map<Long, String> entries = new HashMap<>();
        Long2ObjectMaps.fastIterable(map).forEach(entry ->
            entries.put(entry.getLongKey(), entry.getValue()));

        assertEquals(Map.of(4L, "loaded"), entries);
    }
}
