package dev.aerogel.api;

import dev.aerogel.api.event.EventBus;
import dev.aerogel.api.bossbar.BossBarService;
import dev.aerogel.api.command.CommandService;
import dev.aerogel.api.dialog.DialogService;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.player.PlayerService;
import dev.aerogel.api.scheduler.Scheduler;
import dev.aerogel.api.scoreboard.ScoreboardService;
import dev.aerogel.api.world.WorldService;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Stable context passed to a plugin's pre-launch entry point. */
public interface PluginContext {
    String pluginId();

    String pluginVersion();

    Path serverDirectory();

    Path dataDirectory();

    Logger logger();

    EventBus events();

    AerogelServer server();

    default CommandService commands() { return server().commands(); }
    default Scheduler scheduler() { return server().scheduler(); }
    default InventoryService inventories() { return server().inventories(); }
    default PlayerService players() { return server().players(); }
    default ScoreboardService scoreboards() { return server().scoreboards(); }
    default BossBarService bossBars() { return server().bossBars(); }
    default DialogService dialogs() { return server().dialogs(); }
    default WorldService worlds() { return server().worlds(); }
}
