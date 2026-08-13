package dev.aerogel.loader.install;

import dev.aerogel.loader.util.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerBundleTest {
    @TempDir
    Path directory;

    @Test
    void extractsAndVerifiesMojangBundleIndexes() throws Exception {
        byte[] server = "server classes".getBytes(StandardCharsets.UTF_8);
        byte[] library = "library classes".getBytes(StandardCharsets.UTF_8);
        Path serverFile = directory.resolve("server-source.bin");
        Path libraryFile = directory.resolve("library-source.bin");
        Files.write(serverFile, server);
        Files.write(libraryFile, library);
        String versions = Hashing.sha256(serverFile) + "\t26.2\t26.2/server.jar\n";
        String libraries = Hashing.sha256(libraryFile) + "\texample:lib:1\texample/lib/1/lib.jar\n";
        Path bundler = directory.resolve("server.jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(bundler))) {
            entry(output, "META-INF/versions.list", versions.getBytes(StandardCharsets.UTF_8));
            entry(output, "META-INF/libraries.list", libraries.getBytes(StandardCharsets.UTF_8));
            entry(output, "META-INF/main-class", "example.Main\n".getBytes(StandardCharsets.UTF_8));
            entry(output, "META-INF/versions/26.2/server.jar", server);
            entry(output, "META-INF/libraries/example/lib/1/lib.jar", library);
        }

        ServerBundle result = ServerBundle.extract(bundler, directory.resolve("cache"));

        assertEquals("example.Main", result.mainClass());
        assertEquals(2, result.classPath().size());
        assertTrue(result.classPath().stream().allMatch(Files::isRegularFile));
    }

    private static void entry(JarOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(content);
        output.closeEntry();
    }
}
