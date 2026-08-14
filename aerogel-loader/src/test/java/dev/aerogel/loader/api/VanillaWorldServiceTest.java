package dev.aerogel.loader.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VanillaWorldServiceTest {
    @Test
    void qualifiesPluginOwnedWorldIds() {
        assertEquals("test_plugin:arena",
            VanillaWorldService.qualifiedId("test_plugin", "Arena"));
        assertEquals("shared:arena",
            VanillaWorldService.qualifiedId("test_plugin", " Shared:Arena "));
    }

    @Test
    void rejectsBlankWorldIds() {
        assertThrows(IllegalArgumentException.class,
            () -> VanillaWorldService.qualifiedId("test_plugin", "  "));
    }

    @Test
    void deletesOnlyTheRequestedDimensionTree(@TempDir Path directory) throws Exception {
        Path world = Files.createDirectories(directory.resolve("world"));
        Path target = Files.createDirectories(world.resolve("dimensions/test/arena/region"));
        Files.writeString(target.resolve("r.0.0.mca"), "region");
        Files.writeString(world.resolve("level.dat"), "level");

        VanillaWorldService.deleteTree(world, world.resolve("dimensions/test/arena"));

        assertFalse(Files.exists(world.resolve("dimensions/test/arena")));
        assertEquals("level", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void refusesToDeleteTheWorldRootOrOutsideIt(@TempDir Path directory) throws Exception {
        Path world = Files.createDirectories(directory.resolve("world"));
        Path outside = Files.createDirectories(directory.resolve("outside"));

        assertThrows(IllegalArgumentException.class,
            () -> VanillaWorldService.deleteTree(world, world));
        assertThrows(IllegalArgumentException.class,
            () -> VanillaWorldService.deleteTree(world, outside));
    }
}
