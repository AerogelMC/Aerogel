package dev.aerogel.loader.worldgen;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reproducible real-server throughput benchmark for a 16x16 fresh chunk wave. */
@Tag("chunk-generation-benchmark")
final class ChunkGenerationServerBenchmarkTest {
    private static final int FIRST_CHUNK = 256;
    private static final int CHUNKS_PER_AXIS = 16;
    private static final int EXPECTED_CHUNKS = CHUNKS_PER_AXIS * CHUNKS_PER_AXIS;
    private static final String MARKER = "AEROGEL_CHUNK_READY_";

    @TempDir Path directory;

    @Test
    @Timeout(240)
    void generatesFreshChunkWave() throws Exception {
        Path distribution = requiredPath("aerogel.test.distributionJar");
        Path vanillaServer = requiredPath("aerogel.test.serverJar");
        Path server = directory.resolve("server");
        Files.createDirectories(server.resolve("runtime/26.2"));
        Files.copy(distribution, server.resolve("Aerogel.jar"),
            StandardCopyOption.REPLACE_EXISTING);
        Files.copy(vanillaServer, server.resolve("runtime/26.2/server.jar"),
            StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(server.resolve("eula.txt"), "eula=true\n");
        Files.writeString(server.resolve("server.properties"), """
            online-mode=false
            server-port=0
            level-seed=6840222118194611220
            generate-structures=true
            spawn-protection=0
            view-distance=2
            simulation-distance=2
            max-tick-time=0
            broadcast-console-to-ops=false
            """);

        Process process = new ProcessBuilder(javaExecutable(), "-Xms2G", "-Xmx4G",
            "-jar", "Aerogel.jar", "nogui")
            .directory(server.toFile())
            .redirectErrorStream(true)
            .start();
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        Set<String> readyChunks = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch ready = new CountDownLatch(1);
        Thread reader = Thread.ofPlatform().name("chunk-benchmark-output").start(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    output.add(line);
                    if (line.contains("Done (")) ready.countDown();
                    int marker = line.indexOf(MARKER);
                    if (marker >= 0) readyChunks.add(line.substring(marker).trim());
                }
            } catch (Exception failure) {
                output.add("reader failed: " + failure);
            }
        });

        long elapsedMillis;
        ProcessHandle serverProcess = null;
        try (BufferedWriter input = new BufferedWriter(new OutputStreamWriter(
            process.getOutputStream(), StandardCharsets.UTF_8))) {
            assertTrue(ready.await(60, TimeUnit.SECONDS), () -> joined(output));
            serverProcess = minecraftServerProcess(process);
            Path jfrOutput = optionalPath("aerogel.test.jfrOutput");
            if (jfrOutput != null) {
                Files.createDirectories(jfrOutput.getParent());
                jcmd(serverProcess, "JFR.start", "name=AerogelChunkGeneration",
                    "settings=profile");
            }
            int firstBlock = FIRST_CHUNK << 4;
            int lastBlock = ((FIRST_CHUNK + CHUNKS_PER_AXIS) << 4) - 1;
            long started = System.nanoTime();
            send(input, "forceload add " + firstBlock + " " + firstBlock + " "
                + lastBlock + " " + lastBlock);

            long deadline = started + Duration.ofSeconds(150).toNanos();
            while (readyChunks.size() < EXPECTED_CHUNKS
                && System.nanoTime() < deadline && process.isAlive()) {
                for (int x = FIRST_CHUNK; x < FIRST_CHUNK + CHUNKS_PER_AXIS; x++) {
                    for (int z = FIRST_CHUNK; z < FIRST_CHUNK + CHUNKS_PER_AXIS; z++) {
                        String id = MARKER + x + "_" + z;
                        if (readyChunks.contains(id)) continue;
                        int blockX = (x << 4) + 8;
                        int blockZ = (z << 4) + 8;
                        send(input, "execute if loaded " + blockX + " 64 " + blockZ
                            + " run say " + id);
                    }
                }
                Thread.sleep(100);
            }
            elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started);
            System.out.println("CHUNK_GENERATION_BENCHMARK chunks=" + EXPECTED_CHUNKS
                + " elapsedMs=" + elapsedMillis + " chunksPerSecond="
                + String.format(java.util.Locale.ROOT, "%.2f",
                    EXPECTED_CHUNKS * 1000.0 / elapsedMillis));
            if (jfrOutput != null) {
                jcmd(serverProcess, "JFR.dump", "name=AerogelChunkGeneration",
                    "filename=" + jfrOutput);
                jcmd(serverProcess, "JFR.stop", "name=AerogelChunkGeneration");
            }
            // The command is only a reproducible request source. Release its
            // tickets before shutdown so the benchmark does not measure an
            // unrelated in-progress tick wave while the server is stopping.
            send(input, "forceload remove all");
            Thread.sleep(3000);
            send(input, "stop");
        }

        boolean stopped = process.waitFor(15, TimeUnit.SECONDS);
        if (!stopped) {
            if (serverProcess != null && serverProcess.isAlive()) {
                System.out.println("CHUNK_GENERATION_SHUTDOWN_DUMP\n"
                    + jcmd(serverProcess, "Thread.print", "-l"));
            }
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        System.out.println("CHUNK_GENERATION_SHUTDOWN graceful=" + stopped);
        reader.join(TimeUnit.SECONDS.toMillis(5));
        String log = joined(output);
        assertEquals(EXPECTED_CHUNKS, readyChunks.size(), log);
        assertFalse(log.contains("Exception"), log);
        assertFalse(log.contains("Chunk context task failed"), log);
        if (stopped) assertEquals(0, process.exitValue(), log);
    }

    private static void send(BufferedWriter writer, String command) throws Exception {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property " + property);
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing file " + path);
        return path;
    }

    private static Path optionalPath(String property) {
        String value = System.getProperty(property);
        return value == null || value.isBlank()
            ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private static ProcessHandle minecraftServerProcess(Process launcher) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        java.time.Instant earliest = launcher.info().startInstant()
            .orElse(java.time.Instant.now()).minusSeconds(1);
        while (System.nanoTime() < deadline) {
            ProcessHandle child = listedMinecraftServer(earliest);
            if (child != null) return child;
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
        throw new IllegalStateException("Minecraft server process not found");
    }

    private static ProcessHandle listedMinecraftServer(java.time.Instant earliest) {
        try {
            Process listing = new ProcessBuilder(javaTool("jcmd"), "-l")
                .redirectErrorStream(true).start();
            String output = new String(listing.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            if (!listing.waitFor(10, TimeUnit.SECONDS) || listing.exitValue() != 0) {
                return null;
            }
            for (String line : output.split("\\R")) {
                if (!line.contains("AerogelServerBootstrap")) continue;
                int separator = line.indexOf(' ');
                if (separator <= 0) continue;
                ProcessHandle handle = ProcessHandle.of(
                    Long.parseLong(line.substring(0, separator))).orElse(null);
                if (handle != null && !handle.info().startInstant()
                    .orElse(java.time.Instant.EPOCH).isBefore(earliest)) {
                    return handle;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String jcmd(ProcessHandle target, String... arguments)
        throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaTool("jcmd"));
        command.add(Long.toString(target.pid()));
        Collections.addAll(command, arguments);
        Process commandProcess = new ProcessBuilder(command)
            .redirectErrorStream(true).start();
        String output = new String(commandProcess.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        if (!commandProcess.waitFor(15, TimeUnit.SECONDS)
            || commandProcess.exitValue() != 0) {
            throw new IllegalStateException("jcmd failed: " + output);
        }
        return output;
    }

    private static String javaTool(String tool) {
        String executable = System.getProperty("os.name").startsWith("Windows")
            ? tool + ".exe" : tool;
        return Path.of(System.getProperty("java.home"), "bin", executable)
            .toString();
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").startsWith("Windows")
            ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String joined(List<String> output) {
        synchronized (output) {
            return String.join(System.lineSeparator(), output);
        }
    }
}
