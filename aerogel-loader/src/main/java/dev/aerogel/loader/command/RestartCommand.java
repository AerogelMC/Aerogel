package dev.aerogel.loader.command;

import com.mojang.brigadier.context.CommandContext;
import dev.aerogel.loader.restart.RestartCoordinator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Registers Aerogel's full-process /restart command against vanilla Brigadier. */
public final class RestartCommand {
    private RestartCommand() { }

    public static void register(Object minecraftServer) {
        MinecraftServer server = (MinecraftServer) minecraftServer;
        server.getCommands().getDispatcher().register(Commands.literal("restart")
            .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .executes(context -> restart(context, server)));
    }

    private static int restart(
        CommandContext<CommandSourceStack> context, MinecraftServer server
    ) {
        if (!RestartCoordinator.available()) {
            sendFailure(context, "commands.aerogel.restart.unavailable",
                "Automatic restart is unavailable because Aerogel is not running under its launcher.");
            return 0;
        }
        if (RestartCoordinator.requested()) {
            sendFailure(context, "commands.aerogel.restart.already",
                "A server restart is already in progress.");
            return 0;
        }
        if (!RestartCoordinator.request(server)) {
            sendFailure(context, "commands.aerogel.restart.prepare_failed",
                "The server could not prepare for restart. See the console for details.");
            return 0;
        }
        return 1;
    }

    private static void sendFailure(
        CommandContext<CommandSourceStack> context, String key, String fallback
    ) {
        CommandSourceStack source = context.getSource();
        String localized = CommandTranslations.fallback(sourceLanguage(source), key, fallback);
        source.sendFailure(Component.translatableWithFallback(key, localized));
    }

    private static String sourceLanguage(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? "en_us" : player.clientInformation().language();
    }
}
