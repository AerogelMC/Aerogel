package dev.aerogel.api;

import dev.aerogel.api.event.EventBus;
import dev.aerogel.api.bossbar.BossBarService;
import dev.aerogel.api.command.CommandService;
import dev.aerogel.api.dialog.DialogService;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.scheduler.Scheduler;
import dev.aerogel.api.scoreboard.ScoreboardService;
import dev.aerogel.api.storage.StorageService;
import dev.aerogel.api.translation.TranslationService;
import dev.aerogel.api.world.WorldService;
import net.minecraft.server.MinecraftServer;

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

    /** Returns the live server owned by Aerogel. Plugins must not close it. */
    default MinecraftServer minecraft() { return server().vanilla(); }

    default CommandService commands() { return server().commands(); }
    default Scheduler scheduler() { return server().scheduler(); }
    default InventoryService inventories() { return server().inventories(); }
    default ScoreboardService scoreboards() { return server().scoreboards(); }
    default BossBarService bossBars() { return server().bossBars(); }
    default DialogService dialogs() { return server().dialogs(); }
    default TranslationService translations() { return server().translations(); }
    default StorageService storage() { return server().storage(); }
    default WorldService worlds() { return server().worlds(); }
}
