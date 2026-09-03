package dev.aerogel.loader.context;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-server regression for cross-Context redstone and command-block tick chains. */
@Tag("neighbor-circuit")
final class NeighborCircuitServerIntegrationTest {
    private static final String COMMAND_TICK_MARKER = "AEROGEL_NEIGHBOR_COMMAND_TICKS_OK";
    private static final String RELOAD_MARKER = "AEROGEL_SAVED_CHUNK_RELOAD_OK";
    private static final int TICKS_TO_RUN = 160;
    private static final List<String> FAILURES = List.of(
        "Too many chained neighbor updates",
        "ConcurrentModificationException",
        "Chunk context task failed",
        "Aerogel neighbor-chain limit origin"
    );

    @TempDir Path directory;

    @Test
    @Timeout(120)
    void boundaryCircuitAndRepeatingCommandsDoNotCorruptNeighborQueues() throws Exception {
        boolean vanillaBaseline = Boolean.getBoolean("aerogel.test.vanillaBaseline");
        Path vanillaServer = requiredPath("aerogel.test.serverJar");
        Path server = directory.resolve("server");
        String launchJar;
        if (vanillaBaseline) {
            Files.createDirectories(server);
            Files.copy(vanillaServer, server.resolve("server.jar"),
                StandardCopyOption.REPLACE_EXISTING);
            launchJar = "server.jar";
        } else {
            Path distribution = requiredPath("aerogel.test.distributionJar");
            Files.createDirectories(server.resolve("runtime/26.2"));
            Files.copy(distribution, server.resolve("Aerogel.jar"),
                StandardCopyOption.REPLACE_EXISTING);
            Files.copy(vanillaServer, server.resolve("runtime/26.2/server.jar"),
                StandardCopyOption.REPLACE_EXISTING);
            launchJar = "Aerogel.jar";
        }
        Files.writeString(server.resolve("eula.txt"), "eula=true\n");
        Files.writeString(server.resolve("server.properties"), """
            online-mode=false
            server-port=0
            level-type=minecraft:flat
            generate-structures=false
            spawn-protection=0
            view-distance=4
            simulation-distance=4
            max-tick-time=0
            enable-command-block=true
            broadcast-console-to-ops=false
            broadcast-rcon-to-ops=false
            """);

        Process process = new ProcessBuilder(javaExecutable(), "-Xms1G", "-Xmx2G",
            "-jar", launchJar, "nogui")
            .directory(server.toFile())
            .redirectErrorStream(true)
            .start();
        List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch commandTicksCompleted = new CountDownLatch(1);
        CountDownLatch failureDetected = new CountDownLatch(1);
        Thread reader = Thread.ofPlatform().name("neighbor-circuit-output").start(() ->
            readOutput(process, output, ready, commandTicksCompleted, failureDetected));

        try (BufferedWriter input = new BufferedWriter(new OutputStreamWriter(
            process.getOutputStream(), StandardCharsets.UTF_8))) {
            assertTrue(ready.await(45, TimeUnit.SECONDS), () -> joined(output));
            // The generated server has no player to hold the fixture's chunks.
            // Load exactly the circuit footprint before placing any blocks.
            send(input, "forceload add -48 -48 47 47");
            Thread.sleep(Duration.ofSeconds(2));
            for (String command : circuitCommands()) send(input, command);
            // Loading the fixture is not the regression trigger. Start one real
            // vanilla neighbor chain by changing the lever's powered block state,
            // exactly as a player interaction does after LeverBlock.useWithoutItem.
            send(input, "setblock -48 63 -48 minecraft:lever[face=floor,"
                + "facing=east,powered=true]");

            // Count real game ticks rather than wall-clock time. This remains exact
            // even when the implementation under test is lagging.
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (failureDetected.getCount() != 0
                && !commandTicksCompleted.await(250, TimeUnit.MILLISECONDS)
                && System.nanoTime() < deadline) {
                send(input, "execute if score ticks aerogel_neighbor_test matches "
                    + TICKS_TO_RUN + ".. run say " + COMMAND_TICK_MARKER);
            }
            send(input, "stop");
        } catch (IOException error) {
            throw new AssertionError("Server input closed unexpectedly:\n" + joined(output), error);
        } finally {
            long shutdownSeconds = failureDetected.getCount() == 0 ? 5 : 30;
            if (!process.waitFor(shutdownSeconds, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            reader.join(TimeUnit.SECONDS.toMillis(5));
        }

        String log = joined(output);
        assertTrue(log.contains("Done ("), log);
        for (String failure : FAILURES) assertFalse(log.contains(failure), log);
        assertEquals(0, process.exitValue(), log);
        assertTrue(log.contains(COMMAND_TICK_MARKER),
            "Repeating command blocks did not execute for " + TICKS_TO_RUN
                + " ticks:\n" + log);
        verifySavedChunkReload(server, launchJar);
    }

    private static void verifySavedChunkReload(Path server, String launchJar)
        throws Exception {
        Process process = new ProcessBuilder(javaExecutable(), "-Xms1G", "-Xmx2G",
            "-jar", launchJar, "nogui")
            .directory(server.toFile())
            .redirectErrorStream(true)
            .start();
        List<String> output = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch reloaded = new CountDownLatch(1);
        Thread reader = Thread.ofPlatform().name("saved-chunk-reload-output").start(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    output.add(line);
                    if (line.contains("Done (")) ready.countDown();
                    if (line.contains(RELOAD_MARKER)) reloaded.countDown();
                }
            } catch (IOException error) {
                output.add("reader failed: " + error);
            }
        });

        try (BufferedWriter input = new BufferedWriter(new OutputStreamWriter(
            process.getOutputStream(), StandardCharsets.UTF_8))) {
            assertTrue(ready.await(45, TimeUnit.SECONDS), () -> joined(output));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (!reloaded.await(100, TimeUnit.MILLISECONDS)
                && System.nanoTime() < deadline) {
                send(input, "execute if loaded 0 64 0 run say " + RELOAD_MARKER);
            }
            send(input, "stop");
        } finally {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            reader.join(TimeUnit.SECONDS.toMillis(5));
        }

        String log = joined(output);
        assertTrue(log.contains(RELOAD_MARKER), log);
        assertFalse(log.contains("Exception"), log);
        assertFalse(log.contains("Chunk context task failed"), log);
        assertEquals(0, process.exitValue(), log);
    }

    private static List<String> circuitCommands() {
        List<String> commands = new ArrayList<>();
        commands.add("scoreboard objectives add aerogel_neighbor_test dummy");
        // A 96x96 wire sheet crosses six chunk faces in both axes. Repeaters on
        // every eighth row force scheduled-tick queries into the same network.
        commands.add("fill -48 62 -48 47 62 47 minecraft:stone");
        commands.add("fill -48 63 -48 47 63 47 minecraft:redstone_wire");
        commands.add("setblock -48 63 -48 minecraft:lever[face=floor,"
            + "facing=east,powered=false]");
        for (int z = -48; z <= 47; z += 8) {
            for (int x = -47; x <= 46; x += 8) {
                commands.add("setblock " + x + " 63 " + z
                    + " minecraft:repeater[facing=east]");
            }
        }

        // Paired repeating command blocks toggle power on opposite sides of each
        // chunk face. Additional entity commands reproduce the command-heavy live
        // workload without requiring a player or a checked-in world fixture.
        int[][] points = {
            {-48, -32}, {-32, -16}, {-16, 0}, {0, 16}, {16, 32}, {32, 47}
        };
        int commandX = -40;
        for (int[] point : points) {
            String target = point[0] + " 63 " + point[1];
            commands.add(repeating(commandX++, 60, -40,
                "setblock " + target + " minecraft:redstone_block"));
            commands.add(repeating(commandX++, 60, -40,
                "setblock " + target + " minecraft:redstone_wire"));
        }
        commands.add(repeating(-40, 60, -38,
            "summon minecraft:villager 15.5 64 15.5"));
        commands.add(repeating(-39, 60, -38,
            "kill @e[type=minecraft:villager,distance=..24,limit=8,sort=oldest]"));
        commands.add(repeating(-38, 60, -38,
            "scoreboard players add ticks aerogel_neighbor_test 1"));
        return commands;
    }

    private static String repeating(int x, int y, int z, String command) {
        String escaped = command.replace("\\", "\\\\").replace("\"", "\\\"");
        return "setblock " + x + " " + y + " " + z
            + " minecraft:repeating_command_block{Command:\"" + escaped
            + "\",auto:1b,TrackOutput:0b}";
    }

    private static void send(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    private static void readOutput(
        Process process, List<String> output, CountDownLatch ready,
        CountDownLatch commandTicksCompleted, CountDownLatch failureDetected
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                if (line.contains("Done (")) ready.countDown();
                if (line.contains(COMMAND_TICK_MARKER)) commandTicksCompleted.countDown();
                for (String failure : FAILURES) {
                    if (line.contains(failure)) {
                        failureDetected.countDown();
                        break;
                    }
                }
            }
        } catch (IOException error) {
            output.add("reader failed: " + error);
        }
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
