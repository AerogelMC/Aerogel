package dev.aerogel.loader;

import dev.aerogel.loader.cli.LaunchOptions;
import dev.aerogel.loader.install.MinecraftInstaller;

import java.io.IOException;
import java.nio.file.Files;

public final class AerogelMain {
    private AerogelMain() {
    }

    public static void main(String[] args) {
        int status;
        try {
            status = execute(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("[Aerogel] " + exception.getMessage());
            System.err.println("Use 'aerogel help' for usage.");
            status = 2;
        } catch (Exception exception) {
            System.err.println("[Aerogel] " + exception.getMessage());
            exception.printStackTrace(System.err);
            status = 1;
        }
        if (status != 0) {
            System.exit(status);
        }
    }

    static int execute(String[] args) throws Exception {
        BuildInfo build = BuildInfo.current();
        LaunchOptions options = LaunchOptions.parse(args, build.minecraftVersion());
        MinecraftInstaller.requireSupportedVersion(options.minecraftVersion());
        return switch (options.command()) {
            case HELP -> {
                printHelp();
                yield 0;
            }
            case VERSION -> {
                System.out.printf("Aerogel %s (Minecraft %s+, Mixin engine %s)%n",
                    build.version(), build.minecraftVersion(), build.mixinVersion());
                yield 0;
            }
            case SETUP -> setup(options);
            case DOCTOR -> doctor(build, options);
            case RUN -> run(build, options, args);
        };
    }

    private static int run(BuildInfo build, LaunchOptions options, String[] arguments)
        throws IOException, InterruptedException {
        Integer snapshotStatus = RuntimeSnapshot.relaunchIfNeeded(options.gameDirectory(), arguments);
        if (snapshotStatus != null) return snapshotStatus;
        if (!Files.isRegularFile(options.serverJar())) {
            int setupStatus = setup(options);
            if (setupStatus != 0) {
                return setupStatus;
            }
        }
        return new ServerProcessLauncher(build).launch(options);
    }

    private static int setup(LaunchOptions options) throws IOException, InterruptedException {
        Files.createDirectories(options.gameDirectory());
        MinecraftInstaller installer = new MinecraftInstaller();
        MinecraftInstaller.Installation result = installer.install(
            options.minecraftVersion(), options.serverJar(), options.gameDirectory()
        );
        Files.createDirectories(options.gameDirectory().resolve("plugins"));
        Files.createDirectories(options.gameDirectory().resolve("config"));
        System.out.printf("[Aerogel] Installed official Minecraft %s server (%d bytes).%n",
            result.version(), result.size());
        System.out.println("[Aerogel] SHA-1 verified: " + result.sha1());
        System.out.println("[Aerogel] Server directory: " + options.gameDirectory());
        System.out.println("[Aerogel] EULA acceptance is not automated. Read https://aka.ms/MinecraftEULA");
        System.out.println("[Aerogel] On first launch, edit the generated eula.txt and set eula=true if you agree.");
        return 0;
    }

    private static int doctor(BuildInfo build, LaunchOptions options) throws IOException {
        boolean failed = false;
        for (String result : new ServerProcessLauncher(build).diagnose(options)) {
            System.out.println("[Aerogel] " + result);
            failed |= result.startsWith("FAIL");
        }
        return failed ? 1 : 0;
    }

    private static void printHelp() {
        System.out.println("""
            Aerogel - Minecraft 26.2+ dedicated-server Mixin loader

            Commands:
              aerogel setup [options]
              aerogel run [options] [-- Minecraft server arguments]
              aerogel doctor [options]
              aerogel version

            Options:
              --game-dir <path>       Server directory (default: current directory)
              --minecraft <version>   Release to install/run (default: 26.2)
              --server-jar <path>     Use a specific official server JAR
              --jvm-arg <argument>    Child JVM argument; repeatable, e.g. -Xmx4G
              --gui                   Do not append the default 'nogui' argument
              --offline               Reserved for offline verification workflows
              --                      Pass all remaining arguments to Minecraft

            Run: java -Xms2G -Xmx4G -jar Aerogel-26.2-27.jar nogui
            First launch creates eula.txt and stops. Read the EULA, then edit the file directly.
            Plugins go in <game-dir>/plugins and contain aerogel.plugin.json.
            """);
    }
}
