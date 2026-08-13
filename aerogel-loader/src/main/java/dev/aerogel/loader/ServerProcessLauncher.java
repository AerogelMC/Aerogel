package dev.aerogel.loader;

import dev.aerogel.loader.cli.LaunchOptions;
import dev.aerogel.loader.plugin.PluginDiscovery;
import dev.aerogel.loader.runtime.AerogelServerBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ServerProcessLauncher {
    private final BuildInfo buildInfo;

    public ServerProcessLauncher(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public int launch(LaunchOptions options) throws IOException, InterruptedException {
        verifyRuntime(options);
        new PluginDiscovery().discover(options.gameDirectory().resolve("plugins"), options.minecraftVersion());

        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        if (options.jvmArguments().stream().noneMatch(argument -> argument.startsWith("--enable-native-access"))) {
            command.add("--enable-native-access=ALL-UNNAMED");
        }
        command.addAll(options.jvmArguments());
        command.add("-Daerogel.serverJar=" + options.serverJar());
        command.add("-Daerogel.minecraftVersion=" + options.minecraftVersion());
        command.add("-Daerogel.version=" + buildInfo.version());
        command.add("-cp");
        command.add(absoluteClassPath());
        command.add(AerogelServerBootstrap.class.getName());
        command.addAll(options.serverArguments());
        if (!options.gui() && options.serverArguments().stream().noneMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
            command.add("nogui");
        }
        return new ProcessBuilder(command)
            .directory(options.gameDirectory().toFile())
            .inheritIO()
            .start()
            .waitFor();
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

    private static String absoluteClassPath() {
        Path originalDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Pattern.compile(Pattern.quote(System.getProperty("path.separator")))
            .splitAsStream(System.getProperty("java.class.path"))
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path.normalize() : originalDirectory.resolve(path).normalize())
            .map(Path::toString)
            .reduce((left, right) -> left + System.getProperty("path.separator") + right)
            .orElseThrow(() -> new IllegalStateException("Empty Java classpath"));
    }
}
