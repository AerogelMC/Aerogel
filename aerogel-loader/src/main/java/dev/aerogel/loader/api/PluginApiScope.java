package dev.aerogel.loader.api;

import dev.aerogel.api.AerogelServer;
import dev.aerogel.api.Registration;
import dev.aerogel.api.bossbar.BossBarService;
import dev.aerogel.api.command.CommandService;
import dev.aerogel.api.dialog.DialogService;
import dev.aerogel.api.inventory.InventoryService;
import dev.aerogel.api.scheduler.Scheduler;
import dev.aerogel.api.scoreboard.ScoreboardService;
import net.minecraft.server.MinecraftServer;

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
    private final ReflectiveCommandService commands;
    private final TickScheduler scheduler;
    private final ReflectiveInventoryService inventories;
    private final ReflectiveScoreboardService scoreboards;
    private final ReflectiveBossBarService bossBars;
    private final ReflectiveDialogService dialogs;

    PluginApiScope(AerogelApiRuntime runtime, String pluginId, Logger logger) {
        this.runtime = runtime;
        this.pluginId = pluginId;
        this.logger = logger;
        commands = new ReflectiveCommandService(this);
        scheduler = new TickScheduler(this);
        inventories = new ReflectiveInventoryService(this);
        scoreboards = new ReflectiveScoreboardService(this);
        bossBars = new ReflectiveBossBarService(this);
        dialogs = new ReflectiveDialogService(this);
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

    void serverReady() { commands.serverReady(); }
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

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
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
    }
}
