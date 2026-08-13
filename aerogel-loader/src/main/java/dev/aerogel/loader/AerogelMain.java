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
            case RUN -> new ServerProcessLauncher(build).launch(options);
        };
    }

    private static int setup(LaunchOptions options) throws IOException, InterruptedException {
        if (!options.acceptsMinecraftEula()) {
            System.err.println("Aerogel does not accept Mojang/Minecraft terms for you.");
            System.err.println("Read https://aka.ms/MinecraftEULA and https://www.minecraft.net/usage-guidelines");
            System.err.println("Then rerun with --accept-minecraft-eula if you agree.");
            return 2;
        }
        Files.createDirectories(options.gameDirectory());
        MinecraftInstaller installer = new MinecraftInstaller();
        MinecraftInstaller.Installation result = installer.install(
            options.minecraftVersion(), options.serverJar(), options.gameDirectory()
        );
        MinecraftInstaller.acceptEula(options.gameDirectory());
        Files.createDirectories(options.gameDirectory().resolve("plugins"));
        Files.createDirectories(options.gameDirectory().resolve("config"));
        System.out.printf("[Aerogel] Installed official Minecraft %s server (%d bytes).%n",
            result.version(), result.size());
        System.out.println("[Aerogel] SHA-1 verified: " + result.sha1());
        System.out.println("[Aerogel] Server directory: " + options.gameDirectory());
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
              aerogel setup --accept-minecraft-eula [options]
              aerogel run [options] [-- Minecraft server arguments]
              aerogel doctor [options]
              aerogel version

            Options:
              --game-dir <path>       Server directory (default: ./server)
              --minecraft <version>   Release to install/run (default: 26.2)
              --server-jar <path>     Use a specific official server JAR
              --jvm-arg <argument>    Child JVM argument; repeatable, e.g. -Xmx4G
              --gui                   Do not append the default 'nogui' argument
              --offline               Reserved for offline verification workflows
              --                      Pass all remaining arguments to Minecraft

            Plugins go in <game-dir>/plugins and contain aerogel.plugin.json.
            """);
    }
}
