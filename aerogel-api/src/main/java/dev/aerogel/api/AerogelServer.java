package dev.aerogel.api;

import dev.aerogel.api.bossbar.BossBarService;
import dev.aerogel.api.command.CommandService;
import dev.aerogel.api.dialog.DialogService;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.scheduler.Scheduler;
import dev.aerogel.api.scoreboard.ScoreboardService;
import dev.aerogel.api.translation.TranslationService;
import net.minecraft.server.MinecraftServer;

/** Plugin-owned access to Aerogel conveniences and the live vanilla server. */
public interface AerogelServer {
    boolean ready();

    MinecraftServer vanilla();

    CommandService commands();
    Scheduler scheduler();
    InventoryService inventories();
    ScoreboardService scoreboards();
    BossBarService bossBars();
    DialogService dialogs();
    TranslationService translations();
}
