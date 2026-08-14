package dev.aerogel.loader.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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

    @Test
    void deletesOnlyDimensionDirectoriesInsideWorld(@TempDir Path directory) throws Exception {
        Path world = Files.createDirectory(directory.resolve("world"));
        Path dimension = Files.createDirectories(world.resolve("dimensions/test/arena/region"));
        Files.writeString(dimension.resolve("r.0.0.mca"), "chunk");

        ReflectiveWorldService.deleteTree(world, world.resolve("dimensions/test/arena"));

        assertEquals(false, Files.exists(world.resolve("dimensions/test/arena")));
        assertEquals(true, Files.isDirectory(world));
    }

    @Test
    void refusesToDeletePrimaryOrOutsideDirectory(@TempDir Path directory) throws Exception {
        Path world = Files.createDirectory(directory.resolve("world"));
        Path outside = Files.createDirectory(directory.resolve("outside"));

        assertThrows(IllegalArgumentException.class,
            () -> ReflectiveWorldService.deleteTree(world, world));
        assertThrows(IllegalArgumentException.class,
            () -> ReflectiveWorldService.deleteTree(world, outside));
    }
}
