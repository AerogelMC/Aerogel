package dev.aerogel.loader.context;

import org.junit.jupiter.api.Test;

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
}
