package dev.aerogel.loader;

import dev.aerogel.loader.util.Hashing;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Runs Aerogel from a content-addressed copy so replacing the distribution JAR cannot corrupt a live JVM. */
final class RuntimeSnapshot {
    private static final String ACTIVE = "aerogel.runtimeSnapshot";
    private static final String DISTRIBUTION = "aerogel.distributionJar";

    private RuntimeSnapshot() {
    }

    static Integer relaunchIfNeeded(Path gameDirectory, String[] arguments)
        throws IOException, InterruptedException {
        Path source = codeSource();
        if (Boolean.getBoolean(ACTIVE) || source == null) return null;
        Path snapshot = prepare(source, gameDirectory);
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.addAll(inheritedJvmArguments());
        command.add("-D" + ACTIVE + "=true");
        command.add("-D" + DISTRIBUTION + "=" + source);
        command.add("-jar");
        command.add(snapshot.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
            .directory(Path.of(System.getProperty("user.dir")).toFile())
            .inheritIO()
            .start();
        return process.waitFor();
    }

    static Path latestOrCurrent(Path gameDirectory) {
        Path current = codeSource();
        String distribution = System.getProperty(DISTRIBUTION);
        if (distribution == null || distribution.isBlank()) return current;
        try {
            return prepare(Path.of(distribution).toAbsolutePath().normalize(), gameDirectory);
        } catch (IOException exception) {
            System.err.println("[Aerogel] Could not stage the updated runtime; continuing with the current snapshot: "
                + exception.getMessage());
            return current;
        }
    }

    static List<String> inheritedJvmArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .filter(argument -> !argument.startsWith("-D" + ACTIVE + "="))
            .filter(argument -> !argument.startsWith("-D" + DISTRIBUTION + "="))
            .filter(argument -> !argument.startsWith("-javaagent:"))
            .toList();
    }

    static Path codeSource() {
        try {
            Path location = Path.of(AerogelMain.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize();
            return Files.isRegularFile(location) ? location : null;
        } catch (java.net.URISyntaxException exception) {
            return null;
        }
    }

    static Path prepare(Path source, Path gameDirectory) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Aerogel distribution JAR not found: " + source);
        IOException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String hash = Hashing.sha256(source);
                Path target = gameDirectory.resolve(".aerogel").resolve("runtime")
                    .resolve(hash).resolve("Aerogel.jar").toAbsolutePath().normalize();
                if (valid(target, hash)) return target;
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), "Aerogel-", ".tmp");
                boolean installed = false;
                try {
                    Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                    if (!Hashing.sha256(temporary).equals(hash) || !validJar(temporary)) {
                        throw new IOException("Aerogel distribution changed while it was being staged");
                    }
                    moveReplacing(temporary, target);
                    installed = true;
                    return target;
                } finally {
                    if (!installed) Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                failure = exception;
                if (attempt < 2) {
                    try { Thread.sleep(100L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while staging the Aerogel runtime", interrupted);
                    }
                }
            }
        }
        throw failure == null ? new IOException("Could not stage the Aerogel runtime") : failure;
    }

    private static boolean valid(Path target, String hash) {
        try { return Files.isRegularFile(target) && Hashing.sha256(target).equals(hash) && validJar(target); }
        catch (IOException ignored) { return false; }
    }

    private static boolean validJar(Path jar) {
        try (JarFile file = new JarFile(jar.toFile(), false)) {
            return file.getJarEntry("dev/aerogel/loader/AerogelMain.class") != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }
}
