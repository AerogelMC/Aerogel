package dev.aerogel.loader.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.aerogel.loader.network.PacketQueueMetrics;
import dev.aerogel.loader.plugin.PluginManager;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import dev.aerogel.api.context.ContextSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Built-in Aerogel commands, registered directly against vanilla Brigadier. */
public final class PluginsCommand {
    private static final String ROOT = "commands.aerogel.plugins.";
    private static volatile MinecraftServer server;

    private PluginsCommand() { }

    public static void register(Object minecraftServer) {
        register((MinecraftServer) minecraftServer);
    }

    private static void register(MinecraftServer minecraftServer) {
        CommandDispatcher<CommandSourceStack> dispatcher = minecraftServer.getCommands().getDispatcher();
        Predicate<CommandSourceStack> gameMasters = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        Predicate<CommandSourceStack> everyone = Commands.hasPermission(Commands.LEVEL_ALL);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("plugins")
            .requires(gameMasters);
        LiteralArgumentBuilder<CommandSourceStack> list = Commands.literal("list")
            .executes(PluginsCommand::list);
        RequiredArgumentBuilder<CommandSourceStack, String> plugin = Commands.argument(
                "plugin", StringArgumentType.word())
            .suggests((context, builder) -> {
                String remaining = builder.getRemainingLowerCase().toLowerCase(Locale.ROOT);
                for (String id : AerogelRuntime.pluginManager().reloadablePluginIds()) {
                    if (id.startsWith(remaining)) builder.suggest(id);
                }
                return builder.buildFuture();
            })
            .executes(PluginsCommand::reloadOne);
        root.then(list).then(Commands.literal("reload")
            .executes(PluginsCommand::reloadAll)
            .then(plugin));
        dispatcher.register(root);

        dispatcher.register(Commands.literal("tps")
            .requires(everyone)
            .executes(context -> tps(context, minecraftServer)));

        LiteralArgumentBuilder<CommandSourceStack> networkStats = Commands.literal("networkstats")
            .requires(everyone)
            .executes(PluginsCommand::networkStats);
        networkStats.then(Commands.literal("reset")
            .requires(gameMasters)
            .executes(PluginsCommand::resetNetworkStats));
        networkStats.then(Commands.literal("mode")
            .requires(gameMasters)
            .then(Commands.literal("aerogel")
                .executes(context -> setNetworkStatsMode(context, true)))
            .then(Commands.literal("vanilla")
                .executes(context -> setNetworkStatsMode(context, false))));
        dispatcher.register(networkStats);
        server = minecraftServer;
    }

    public static List<String> complete(String input, int cursor) {
        MinecraftServer current = server;
        if (current == null) return List.of();
        try {
            String command = input.startsWith("/") ? input.substring(1) : input;
            int commandCursor = input.startsWith("/") ? Math.max(0, cursor - 1) : cursor;
            CommandDispatcher<CommandSourceStack> dispatcher = current.getCommands().getDispatcher();
            ParseResults<CommandSourceStack> results = dispatcher.parse(
                command, current.createCommandSourceStack());
            Suggestions suggestions = dispatcher.getCompletionSuggestions(results, commandCursor)
                .get(750, TimeUnit.MILLISECONDS);
            List<String> completed = new ArrayList<>(suggestions.getList().size());
            for (Suggestion suggestion : suggestions.getList()) completed.add(suggestion.getText());
            return completed;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<PluginManager.PluginInfo> plugins = AerogelRuntime.pluginManager().pluginInfos();
        sendSuccess(context, ROOT + "list", "Plugins (%s): %s",
            plugins.size(), displayPlugins(context, plugins));
        return plugins.size();
    }

    private static int reloadAll(CommandContext<CommandSourceStack> context) {
        sendSuccess(context, ROOT + "reload_all.starting", "Reloading all plugins...");
        long started = System.nanoTime();
        PluginManager.ReloadResult result = AerogelRuntime.pluginManager().reloadAll();
        String elapsed = elapsedSeconds(started);
        if (result.successful()) {
            sendSuccess(context, ROOT + "reload_all.success",
                "Loaded or reloaded %s plugin(s), unloaded %s in %s seconds",
                result.reloaded().size(), result.unloaded().size(), elapsed);
        } else {
            sendFailure(context, ROOT + "reload_partial",
                "Loaded or reloaded %s plugin(s), unloaded %s in %s seconds; failed: %s",
                result.reloaded().size(), result.unloaded().size(), elapsed,
                String.join(", ", result.failures().keySet()));
        }
        sendMixinNotice(context, result.mixinRestartRequired());
        return result.reloaded().size() + result.unloaded().size();
    }

    private static int reloadOne(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "plugin");
        sendSuccess(context, ROOT + "reload_one.starting", "Reloading plugin %s...", id);
        long started = System.nanoTime();
        Optional<PluginManager.ReloadResult> optional = AerogelRuntime.pluginManager().reload(id);
        if (optional.isEmpty()) {
            sendFailure(context, ROOT + "unknown", "Unknown plugin: %s", id);
            return 0;
        }
        PluginManager.ReloadResult result = optional.get();
        String elapsed = elapsedSeconds(started);
        if (!result.successful()) {
            sendFailure(context, ROOT + "reload_failed",
                "Could not reload plugin %s after %s seconds", id, elapsed);
            sendMixinNotice(context, result.mixinRestartRequired());
            return 0;
        }
        if (result.unloaded().contains(id)) {
            sendSuccess(context, ROOT + "reload_one.unloaded",
                "Unloaded plugin %s in %s seconds", id, elapsed);
            sendMixinNotice(context, result.mixinRestartRequired());
            return 1;
        }
        sendSuccess(context, ROOT + "reload_one.success",
            "Reloaded plugin %s in %s seconds", id, elapsed);
        sendMixinNotice(context, result.mixinRestartRequired());
        return 1;
    }

    private static void sendMixinNotice(
        CommandContext<CommandSourceStack> context, List<String> pluginIds
    ) {
        if (!pluginIds.isEmpty()) {
            sendWarning(context, ROOT + "mixin_notice",
                "Some Mixin changes for %s may not be applied until the server restarts",
                String.join(", ", pluginIds));
        }
    }

    private static int tps(
        CommandContext<CommandSourceStack> context, MinecraftServer minecraftServer
    ) {
        TpsMonitor.Snapshot snapshot = TpsMonitor.snapshot();
        double targetTps = minecraftServer.tickRateManager().tickrate();
        sendSuccess(context, "commands.aerogel.tps",
            "TPS (1m, 5m, 15m): %s, %s, %s | MSPT: %s",
            tpsValue(snapshot.oneMinute(), targetTps),
            tpsValue(snapshot.fiveMinutes(), targetTps),
            tpsValue(snapshot.fifteenMinutes(), targetTps),
            decimal(minecraftServer.getAverageTickTimeNanos() / 1_000_000.0));
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            ChunkPos position = player.chunkPosition();
            ContextSnapshot chunk = AerogelRuntime.playerChunkSnapshot(player);
            sendSuccess(context, "commands.aerogel.tps.chunk",
                "Current chunk [%s, %s] MSPT: %s | max: %s | queued: %s",
                position.x(), position.z(), decimal(chunk.averageExecutionMillis()),
                decimal(chunk.maximumExecutionNanos() / 1_000_000.0D),
                chunk.queuedTasks());
        }
        return 1;
    }

    private static Component tpsValue(double value, double targetTps) {
        return Component.literal(formatTps(value, targetTps)).withStyle(tpsColor(value, targetTps));
    }

    static String formatTps(double value, double targetTps) {
        if (value >= targetTps) {
            return String.format(Locale.ROOT, "%.1f*", targetTps);
        }
        return decimal(value);
    }

    static ChatFormatting tpsColor(double value, double targetTps) {
        if (value >= targetTps * 0.95D) return ChatFormatting.GREEN;
        if (value >= targetTps * 0.90D) return ChatFormatting.YELLOW;
        return ChatFormatting.RED;
    }

    private static int networkStats(CommandContext<CommandSourceStack> context) {
        PacketQueueMetrics.Snapshot snapshot = PacketQueueMetrics.snapshot();
        sendNetworkStatsMode(context, false);
        if (snapshot.samples() == 0L) {
            sendSuccess(context, "commands.aerogel.networkstats.empty",
                "No inbound packet queue samples have been recorded yet.");
            return 1;
        }
        sendSuccess(context, "commands.aerogel.networkstats.summary",
            "Packet queue delay (%s packets over %s seconds): avg %s ms | p50 %s ms | "
                + "p95 %s ms | p99 %s ms | max %s ms",
            grouped(snapshot.samples()), decimal(snapshot.elapsedNanos() / 1_000_000_000.0D),
            milliseconds(snapshot.averageDelayNanos()), milliseconds(snapshot.p50DelayNanos()),
            milliseconds(snapshot.p95DelayNanos()), milliseconds(snapshot.p99DelayNanos()),
            milliseconds(snapshot.maximumDelayNanos()));
        sendSuccess(context, "commands.aerogel.networkstats.paths",
            "Idle pump: %s%% (%s) | Tick boundary: %s%% (%s)",
            percentage(snapshot.idlePumpRatio()), grouped(snapshot.idlePumpSamples()),
            percentage(snapshot.tickBoundaryRatio()), grouped(snapshot.tickBoundarySamples()));
        return 1;
    }

    private static int resetNetworkStats(CommandContext<CommandSourceStack> context) {
        PacketQueueMetrics.reset();
        sendSuccess(context, "commands.aerogel.networkstats.reset",
            "Inbound packet queue statistics were reset.");
        return 1;
    }

    private static int setNetworkStatsMode(
        CommandContext<CommandSourceStack> context, boolean aerogel
    ) {
        PacketQueueMetrics.setIdlePumpEnabled(aerogel);
        sendNetworkStatsMode(context, true);
        return 1;
    }

    private static void sendNetworkStatsMode(
        CommandContext<CommandSourceStack> context, boolean measurementRestarted
    ) {
        boolean enabled = PacketQueueMetrics.idlePumpEnabled();
        sendSuccess(context,
            measurementRestarted
                ? "commands.aerogel.networkstats.mode_changed." + (enabled ? "aerogel" : "vanilla")
                : "commands.aerogel.networkstats.mode." + (enabled ? "aerogel" : "vanilla"),
            measurementRestarted
                ? "Packet handling mode changed to " + (enabled ? "Aerogel idle pump" : "vanilla tick boundary")
                    + ". Statistics were reset."
                : "Packet handling mode: " + (enabled ? "Aerogel idle pump." : "vanilla tick boundary."));
    }

    private static Component displayPlugins(
        CommandContext<CommandSourceStack> context, List<PluginManager.PluginInfo> plugins
    ) {
        if (plugins.isEmpty()) {
            return Component.literal(CommandTranslations.fallback(
                sourceLanguage(context.getSource()), ROOT + "none", "None"));
        }
        MutableComponent result = Component.empty();
        for (int index = 0; index < plugins.size(); index++) {
            PluginManager.PluginInfo plugin = plugins.get(index);
            if (index > 0) result.append(", ");
            MutableComponent name = Component.literal(
                plugin.name().equals(plugin.id()) ? plugin.id() : plugin.name());
            if (!plugin.enabled()) name.withStyle(ChatFormatting.RED);
            result.append(name);
            if (!plugin.name().equals(plugin.id())) {
                result.append(Component.literal(" <" + plugin.id() + ">")
                    .withStyle(ChatFormatting.GRAY));
            }
            if (!plugin.enabled()) {
                Component status = component(context.getSource(), ROOT + "disabled", "Disabled")
                    .copy().withStyle(ChatFormatting.RED);
                result.append(" — ").append(status);
            }
        }
        return result;
    }

    private static void sendSuccess(
        CommandContext<CommandSourceStack> context, String key, String fallback, Object... args
    ) {
        Component message = component(context.getSource(), key, fallback, args);
        context.getSource().sendSuccess(() -> message, false);
    }

    private static void sendFailure(
        CommandContext<CommandSourceStack> context, String key, String fallback, Object... args
    ) {
        context.getSource().sendFailure(component(context.getSource(), key, fallback, args));
    }

    private static void sendWarning(
        CommandContext<CommandSourceStack> context, String key, String fallback, Object... args
    ) {
        MutableComponent message = component(context.getSource(), key, fallback, args)
            .copy().withStyle(ChatFormatting.YELLOW);
        context.getSource().sendSuccess(() -> message, false);
    }

    private static Component component(
        CommandSourceStack source, String key, String fallback, Object... args
    ) {
        return Component.translatableWithFallback(key,
            CommandTranslations.fallback(sourceLanguage(source), key, fallback), args);
    }

    private static String sourceLanguage(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? "en_us" : player.clientInformation().language();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String milliseconds(double nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static String percentage(double ratio) {
        return String.format(Locale.ROOT, "%.1f", ratio * 100.0D);
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String elapsedSeconds(long started) {
        return decimal((System.nanoTime() - started) / 1_000_000_000.0);
    }
}
