package dev.aerogel.loader.runtime;

import dev.aerogel.loader.plugin.PluginManager;
import dev.aerogel.loader.api.AerogelApiRuntime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AerogelRuntime {
    private static volatile PluginManager pluginManager;
    private static volatile AerogelApiRuntime apiRuntime;
    private static final AtomicBoolean pluginsLoaded = new AtomicBoolean();

    private AerogelRuntime() {
    }

    public static void install(PluginManager manager) {
        if (pluginManager != null) {
            throw new IllegalStateException("Aerogel runtime is already installed");
        }
        pluginManager = Objects.requireNonNull(manager, "manager");
        apiRuntime = manager.apiRuntime();
    }

    public static PluginManager pluginManager() {
        PluginManager current = pluginManager;
        if (current == null) {
            throw new IllegalStateException("Aerogel runtime is not installed");
        }
        return current;
    }

    public static void loadPluginsAfterBootstrap() {
        if (!pluginsLoaded.compareAndSet(false, true)) return;
        try {
            pluginManager().loadEntrypoints();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Aerogel plugins after Minecraft bootstrap", exception);
        }
    }

    public static void attachServer(Object server) {
        api().attach(server);
    }

    public static void tick(Object server) {
        api().tick(server);
    }

    private static AerogelApiRuntime api() {
        AerogelApiRuntime current = apiRuntime;
        if (current == null) {
            throw new IllegalStateException("Aerogel API runtime is not installed");
        }
        return current;
    }
}
