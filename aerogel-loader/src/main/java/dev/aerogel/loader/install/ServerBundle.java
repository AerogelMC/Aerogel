package dev.aerogel.loader.install;

import dev.aerogel.loader.util.Hashing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Expands Mojang's supported server-bundler format without modifying any Minecraft artifact. */
public record ServerBundle(String mainClass, List<Path> classPath) {
    private static final String VERSIONS = "META-INF/versions.list";
    private static final String LIBRARIES = "META-INF/libraries.list";
    private static final String MAIN_CLASS = "META-INF/main-class";

    public static ServerBundle extract(Path bundlerJar, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (JarFile jar = new JarFile(bundlerJar.toFile(), false)) {
            JarEntry versions = jar.getJarEntry(VERSIONS);
            JarEntry libraries = jar.getJarEntry(LIBRARIES);
            JarEntry mainClass = jar.getJarEntry(MAIN_CLASS);
            if (versions == null || libraries == null || mainClass == null) {
                throw new IOException("Unsupported server JAR: Mojang server-bundler indexes are missing");
            }
            List<Path> classPath = new ArrayList<>();
            extractList(jar, versions, "META-INF/versions/", destination.resolve("versions"), classPath);
            extractList(jar, libraries, "META-INF/libraries/", destination.resolve("libraries"), classPath);
            String main;
            try (InputStream input = jar.getInputStream(mainClass)) {
                main = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
            if (!main.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
                throw new IOException("Invalid bundled server main class: " + main);
            }
            return new ServerBundle(main, List.copyOf(classPath));
        }
    }

    private static void extractList(JarFile jar, JarEntry index, String resourcePrefix, Path root,
                                    List<Path> classPath) throws IOException {
        Files.createDirectories(root);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(jar.getInputStream(index), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\t");
                if (parts.length != 3 || !parts[0].matches("[0-9a-fA-F]{64}")) {
                    throw new IOException("Malformed Mojang bundle index line: " + line);
                }
                String relative = parts[2].replace('\\', '/');
                Path output = root.resolve(relative).normalize();
                if (!output.startsWith(root.normalize()) || relative.startsWith("/")) {
                    throw new IOException("Unsafe path in Mojang bundle index: " + relative);
                }
                if (!Files.isRegularFile(output) || !Hashing.sha256(output).equalsIgnoreCase(parts[0])) {
                    JarEntry artifact = jar.getJarEntry(resourcePrefix + relative);
                    if (artifact == null || artifact.isDirectory()) {
                        throw new IOException("Bundled server artifact is missing: " + relative);
                    }
                    Files.createDirectories(output.getParent());
                    Path temporary = Files.createTempFile(output.getParent(), "artifact-", ".tmp");
                    boolean complete = false;
                    try (InputStream input = jar.getInputStream(artifact)) {
                        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                        if (!Hashing.sha256(temporary).equalsIgnoreCase(parts[0])) {
                            throw new IOException("Bundled server artifact failed SHA-256 verification: " + relative);
                        }
                        moveReplacing(temporary, output);
                        complete = true;
                    } finally {
                        if (!complete) {
                            Files.deleteIfExists(temporary);
                        }
                    }
                }
                classPath.add(output);
            }
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
