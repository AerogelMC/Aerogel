package dev.aerogel.api;

import dev.aerogel.api.bossbar.BossBarService;
import dev.aerogel.api.command.CommandService;
import dev.aerogel.api.dialog.DialogService;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.loot.LootService;
import dev.aerogel.api.menu.MenuService;
import dev.aerogel.api.persistence.PersistentDataService;
import dev.aerogel.api.recipe.RecipeService;
import dev.aerogel.api.scheduler.Scheduler;
import dev.aerogel.api.scoreboard.ScoreboardService;
import dev.aerogel.api.storage.StorageService;
import dev.aerogel.api.translation.TranslationService;
import dev.aerogel.api.world.WorldService;
import dev.aerogel.api.virtualentity.VirtualEntityService;
import dev.aerogel.api.blockbatch.BlockBatchService;
import dev.aerogel.api.context.ContextService;
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
    StorageService storage();
    WorldService worlds();
    PersistentDataService persistentData();
    RecipeService recipes();
    LootService loot();
    MenuService menus();
    VirtualEntityService virtualEntities();
    BlockBatchService blockBatches();
    ContextService contexts();
}
