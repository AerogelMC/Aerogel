package dev.aerogel.loader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectiveWorldServiceTest {
    @Test
    void qualifiesPluginLocalWorldIds() {
        assertEquals("test_plugin:arena",
            ReflectiveWorldService.qualifiedId("test_plugin", "Arena"));
        assertEquals("shared:arena",
            ReflectiveWorldService.qualifiedId("test_plugin", " Shared:Arena "));
    }

    @Test
    void rejectsBlankWorldIds() {
        assertThrows(IllegalArgumentException.class,
            () -> ReflectiveWorldService.qualifiedId("test_plugin", "  "));
    }
}
