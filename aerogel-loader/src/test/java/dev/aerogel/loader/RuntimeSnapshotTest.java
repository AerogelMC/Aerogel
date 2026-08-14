package dev.aerogel.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeSnapshotTest {
    @TempDir Path directory;

    @Test
    void snapshotsAreContentAddressedAndUnaffectedByDistributionReplacement() throws Exception {
        Path distribution = directory.resolve("Aerogel.jar");
        writeJar(distribution, "one");
        Path first = RuntimeSnapshot.prepare(distribution, directory.resolve("server"));

        writeJar(distribution, "two");
        Path second = RuntimeSnapshot.prepare(distribution, directory.resolve("server"));

        assertNotEquals(first, second);
        assertTrue(Files.isRegularFile(first));
        assertTrue(Files.isRegularFile(second));
    }

    private static void writeJar(Path target, String marker) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(target))) {
            output.putNextEntry(new JarEntry("dev/aerogel/loader/AerogelMain.class"));
            output.write(marker.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
