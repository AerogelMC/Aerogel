package dev.aerogel.loader.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record LaunchOptions(
    Command command,
    Path gameDirectory,
    String minecraftVersion,
    Path serverJar,
    boolean gui,
    boolean offline,
    List<String> jvmArguments,
    List<String> serverArguments
) {
    public enum Command {
        SETUP,
        RUN,
        DOCTOR,
        VERSION,
        HELP
    }

    public static LaunchOptions parse(String[] args, String defaultMinecraftVersion) {
        int index = 0;
        Command command = Command.RUN;
        if (args.length > 0 && !args[0].startsWith("-")) {
            Command parsedCommand = switch (args[0]) {
                case "setup" -> Command.SETUP;
                case "run" -> Command.RUN;
                case "doctor" -> Command.DOCTOR;
                case "version" -> Command.VERSION;
                case "help" -> Command.HELP;
                default -> null;
            };
            if (parsedCommand != null) {
                command = parsedCommand;
                index++;
            }
        }

        Path gameDirectory = Path.of(".");
        String minecraftVersion = defaultMinecraftVersion;
        Path serverJar = null;
        boolean gui = false;
        boolean offline = false;
        List<String> jvmArguments = new ArrayList<>();
        List<String> serverArguments = new ArrayList<>();

        while (index < args.length) {
            String argument = args[index++];
            if (argument.equals("--")) {
                while (index < args.length) {
                    serverArguments.add(args[index++]);
                }
                break;
            }
            if (argument.startsWith("--game-dir=")) {
                gameDirectory = Path.of(argument.substring("--game-dir=".length()));
                continue;
            }
            if (argument.startsWith("--minecraft=")) {
                minecraftVersion = argument.substring("--minecraft=".length());
                continue;
            }
            if (argument.startsWith("--server-jar=")) {
                serverJar = Path.of(argument.substring("--server-jar=".length()));
                continue;
            }
            if (argument.startsWith("--jvm-arg=")) {
                jvmArguments.add(argument.substring("--jvm-arg=".length()));
                continue;
            }
            switch (argument) {
                case "--game-dir" -> gameDirectory = Path.of(requireValue(args, index++, argument));
                case "--minecraft" -> minecraftVersion = requireValue(args, index++, argument);
                case "--server-jar" -> serverJar = Path.of(requireValue(args, index++, argument));
                case "--jvm-arg" -> jvmArguments.add(requireValue(args, index++, argument));
                case "--gui" -> gui = true;
                case "--offline" -> offline = true;
                case "--help", "-h" -> command = Command.HELP;
                default -> serverArguments.add(argument);
            }
        }

        gameDirectory = gameDirectory.toAbsolutePath().normalize();
        if (serverJar == null) {
            serverJar = gameDirectory.resolve("runtime").resolve(minecraftVersion).resolve("server.jar");
        } else {
            serverJar = serverJar.toAbsolutePath().normalize();
        }
        return new LaunchOptions(
            command,
            gameDirectory,
            minecraftVersion,
            serverJar,
            gui,
            offline,
            List.copyOf(jvmArguments),
            List.copyOf(serverArguments)
        );
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || Set.of("--", "").contains(args[index])) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }
}
