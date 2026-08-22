package dev.aerogel.loader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarbageCollectorSelectorTest {
    @Test
    void prefersGenerationalShenandoah() throws IOException {
        GarbageCollectorSelector.Selection selection =
            GarbageCollectorSelector.select(arguments -> true);

        assertEquals(List.of("-XX:+UseShenandoahGC",
            "-XX:ShenandoahGCMode=generational"), selection.arguments());
        assertEquals("Generational Shenandoah", selection.displayName());
    }

    @Test
    void fallsBackToZgcWhenShenandoahIsUnavailable() throws IOException {
        GarbageCollectorSelector.Selection selection = GarbageCollectorSelector.select(
            arguments -> arguments.equals(List.of("-XX:+UseZGC")));

        assertEquals(List.of("-XX:+UseZGC"), selection.arguments());
        assertEquals("Generational ZGC", selection.displayName());
    }

    @Test
    void failsWhenNeitherLowPauseCollectorIsAvailable() {
        IOException failure = assertThrows(IOException.class,
            () -> GarbageCollectorSelector.select(arguments -> false));

        assertTrue(failure.getMessage().contains("neither Shenandoah GC nor ZGC"));
    }

    @Test
    void preservesAnExplicitCollector() throws IOException {
        GarbageCollectorSelector.Selection selection = GarbageCollectorSelector.select(
            Path.of("unused"), List.of(), List.of("-XX:+UseG1GC"));

        assertTrue(selection.explicit());
        assertTrue(selection.arguments().isEmpty());
    }
}
