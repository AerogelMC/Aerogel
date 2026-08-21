package dev.aerogel.loader;

import dev.aerogel.loader.cli.LaunchOptions;
import dev.aerogel.loader.plugin.PluginDiscovery;
import dev.aerogel.loader.runtime.AerogelServerBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.jar.JarFile;

public final class ServerProcessLauncher {
    private final BuildInfo buildInfo;

    public ServerProcessLauncher(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public int launch(LaunchOptions options) throws IOException, InterruptedException {
        verifyRuntime(options);
        new PluginDiscovery().discover(options.gameDirectory().resolve("plugins"), options.minecraftVersion());

        Path session = options.gameDirectory().resolve(".aerogel").resolve("restart")
            .resolve(UUID.randomUUID().toString()).toAbsolutePath().normalize();
        Files.createDirectories(session);
        int generation = 0;
        Process current = start(options, session, generation);

        while (true) {
            Path request = session.resolve("request-" + generation + ".properties");
            while (current.isAlive() && !Files.isRegularFile(request)) {
                Thread.sleep(100L);
            }
            if (!Files.isRegularFile(request)) {
                return current.waitFor();
            }

            int nextGeneration = generation + 1;
            Process next;
            try {
                next = start(options, session, nextGeneration);
            } catch (IOException exception) {
                Files.writeString(session.resolve("failed-" + generation), exception.toString(),
                    StandardCharsets.UTF_8);
                current.waitFor();
                throw exception;
            }

            Path ready = session.resolve("ready-" + nextGeneration);
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
            while (next.isAlive() && !Files.isRegularFile(ready) && System.nanoTime() < deadline) {
                Thread.sleep(100L);
            }
            if (!Files.isRegularFile(ready)) {
                String reason = next.isAlive() ? "The replacement server did not become ready in time."
                    : "The replacement server exited with status " + next.exitValue() + ".";
                Files.writeString(session.resolve("failed-" + generation), reason, StandardCharsets.UTF_8);
                if (next.isAlive()) {
                    next.destroy();
                }
                current.waitFor();
                return next.isAlive() ? 1 : next.exitValue();
            }

            Files.writeString(session.resolve("release-" + generation), "ready", StandardCharsets.UTF_8);
            if (!current.waitFor(30, TimeUnit.SECONDS)) {
                current.destroy();
                if (!current.waitFor(5, TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                }
            }
            current = next;
            generation = nextGeneration;
        }
    }

    private Process start(LaunchOptions options, Path restartSession, int generation) throws IOException {
        List<String> command = command(options);
        int classPathIndex = command.indexOf("-cp");
        command.add(classPathIndex, "-Daerogel.restartSession=" + restartSession);
        command.add(classPathIndex + 1, "-Daerogel.restartGeneration=" + generation);
        return new ProcessBuilder(command)
            .directory(options.gameDirectory().toFile())
            .inheritIO()
            .start();
    }

    private List<String> command(LaunchOptions options) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.addAll(RuntimeSnapshot.inheritedJvmArguments());
        if (options.jvmArguments().stream().noneMatch(argument -> argument.startsWith("--enable-native-access"))) {
            command.add("--enable-native-access=ALL-UNNAMED");
        }
        command.addAll(options.jvmArguments());
        int lastContendedFlag = -1;
        for (int index = 0; index < command.size(); index++) {
            if (command.get(index).matches("-XX:[+-]RestrictContended")) {
                lastContendedFlag = index;
            }
        }
        if (lastContendedFlag < 0
            || !command.get(lastContendedFlag).equals("-XX:-RestrictContended")) {
            command.add("-XX:-RestrictContended");
        }
        Path runtimeJar = RuntimeSnapshot.latestOrCurrent(options.gameDirectory());
        Path agentJar = agentJar(runtimeJar);
        if (agentJar != null) {
            command.add("-javaagent:" + agentJar);
        }
        command.add("-Daerogel.serverJar=" + options.serverJar());
        command.add("-Daerogel.minecraftVersion=" + options.minecraftVersion());
        command.add("-Daerogel.version=" + buildInfo.version());
        command.add("-cp");
        command.add(absoluteClassPath(runtimeJar));
        command.add(AerogelServerBootstrap.class.getName());
        command.addAll(options.serverArguments());
        if (!options.gui() && options.serverArguments().stream().noneMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
            command.add("nogui");
        }
        return command;
    }

    public List<String> diagnose(LaunchOptions options) throws IOException {
        List<String> results = new ArrayList<>();
        int javaFeature = Runtime.version().feature();
        results.add((javaFeature >= 25 ? "OK" : "FAIL") + " Java " + javaFeature + " (required: 25+)");
        results.add((Files.isRegularFile(options.serverJar()) ? "OK" : "FAIL") + " server JAR: " + options.serverJar());
        Path plugins = options.gameDirectory().resolve("plugins");
        int count = new PluginDiscovery().discover(plugins, options.minecraftVersion()).size();
        results.add("OK plugin metadata and dependency graph: " + count + " plugin(s)");
        results.add("OK Mixin engine: " + buildInfo.mixinVersion());
        return results;
    }

    private static void verifyRuntime(LaunchOptions options) throws IOException {
        if (Runtime.version().feature() < 25) {
            throw new IOException("Minecraft 26.2 requires Java 25 or newer; running " + Runtime.version());
        }
        if (!Files.isRegularFile(options.serverJar())) {
            throw new IOException("Minecraft server JAR not found: " + options.serverJar()
                + System.lineSeparator() + "Run 'aerogel setup' first.");
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    private static String absoluteClassPath(Path runtimeJar) {
        Path originalDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path currentRuntime = RuntimeSnapshot.codeSource();
        return Pattern.compile(Pattern.quote(System.getProperty("path.separator")))
            .splitAsStream(System.getProperty("java.class.path"))
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path.normalize() : originalDirectory.resolve(path).normalize())
            .map(path -> runtimeJar != null && currentRuntime != null && path.equals(currentRuntime)
                ? runtimeJar : path)
            .map(Path::toString)
            .reduce((left, right) -> left + System.getProperty("path.separator") + right)
            .orElseThrow(() -> new IllegalStateException("Empty Java classpath"));
    }

    private static Path agentJar(Path runtimeJar) {
        try {
            Path location = runtimeJar == null ? RuntimeSnapshot.codeSource() : runtimeJar;
            if (location == null || !Files.isRegularFile(location)) {
                return null;
            }
            try (JarFile jar = new JarFile(location.toFile(), false)) {
                return jar.getJarEntry("org/spongepowered/tools/agent/MixinAgent.class") == null ? null : location;
            }
        } catch (IOException exception) {
            return null;
        }
    }
}
