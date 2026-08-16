package dev.aerogel.loader.api;

import dev.aerogel.api.AerogelServer;
import dev.aerogel.api.Registration;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PluginApiScope implements AerogelServer, AutoCloseable {
    private final AerogelApiRuntime runtime;
    private final String pluginId;
    private final Logger logger;
    private final Deque<Registration> resources = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean loadingCompleted = new AtomicBoolean();
    private final AtomicBoolean unloadStarted = new AtomicBoolean();
    private final DirectCommandService commands;
    private final TickScheduler scheduler;
    private final DirectInventoryService inventories;
    private final DirectScoreboardService scoreboards;
    private final DirectBossBarService bossBars;
    private final DirectDialogService dialogs;
    private final PluginTranslations translations;
    private final ManagedStorageService storage;
    private final VanillaWorldService worlds;

    PluginApiScope(
        AerogelApiRuntime runtime,
        String pluginId,
        Logger logger,
        ClassLoader resourceLoader,
        Path dataDirectory
    ) {
        this.runtime = runtime;
        this.pluginId = pluginId;
        this.logger = logger;
        commands = new DirectCommandService(this);
        commands.beginBatch();
        scheduler = new TickScheduler(this);
        inventories = new DirectInventoryService(this);
        scoreboards = new DirectScoreboardService(this);
        bossBars = new DirectBossBarService(this);
        dialogs = new DirectDialogService(this);
        translations = new PluginTranslations(pluginId, resourceLoader, logger);
        storage = new ManagedStorageService(this, dataDirectory, logger);
        worlds = new VanillaWorldService(this);
    }

    <R extends Registration> R own(R resource) {
        Objects.requireNonNull(resource, "resource");
        synchronized (resources) {
            if (closed.get()) {
                resource.close();
                throw new IllegalStateException("Plugin API scope is closed: " + pluginId);
            }
            resources.addFirst(resource);
        }
        return resource;
    }

    void serverReady() {
        storage.serverReady();
        commands.serverReady();
    }

    /** Completes the atomic command-registration phase for this plugin load. */
    public void completeLoading() {
        if (loadingCompleted.compareAndSet(false, true)) commands.endBatch();
    }

    /** Starts an atomic command-removal phase before plugin callbacks run. */
    public void beginUnload() {
        if (!closed.get() && unloadStarted.compareAndSet(false, true)) {
            commands.beginBatch();
        }
    }
    void tick(long tick) { scheduler.tick(tick); }
    Object serverHandle() {
        Object server = runtime.server();
        if (server == null) throw new IllegalStateException("Minecraft server is not ready yet");
        return server;
    }
    ClassLoader loader() { return serverHandle().getClass().getClassLoader(); }
    Logger logger() { return logger; }
    String pluginId() { return pluginId; }

    @Override public boolean ready() { return runtime.ready(); }
    @Override public MinecraftServer vanilla() { return (MinecraftServer) serverHandle(); }
    @Override public CommandService commands() { return commands; }
    @Override public Scheduler scheduler() { return scheduler; }
    @Override public InventoryService inventories() { return inventories; }
    @Override public ScoreboardService scoreboards() { return scoreboards; }
    @Override public BossBarService bossBars() { return bossBars; }
    @Override public DialogService dialogs() { return dialogs; }
    @Override public TranslationService translations() { return translations; }
    @Override public StorageService storage() { return storage; }
    @Override public WorldService worlds() { return worlds; }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (unloadStarted.compareAndSet(false, true)) commands.beginBatch();
        try {
            synchronized (resources) {
                while (!resources.isEmpty()) {
                    try { resources.removeFirst().close(); }
                    catch (RuntimeException exception) {
                        logger.log(Level.WARNING, "Could not release a plugin-owned API resource", exception);
                    }
                }
            }
            scheduler.close();
            runtime.remove(this);
        } finally {
            if (loadingCompleted.compareAndSet(false, true)) commands.endBatch();
            commands.endBatch();
        }
    }
}
