package dev.aerogel.loader.runtime;

import dev.aerogel.loader.plugin.PluginManager;

import java.util.Objects;

public final class AerogelRuntime {
    private static volatile PluginManager pluginManager;

    private AerogelRuntime() {
    }

    public static void install(PluginManager manager) {
        if (pluginManager != null) {
            throw new IllegalStateException("Aerogel runtime is already installed");
        }
        pluginManager = Objects.requireNonNull(manager, "manager");
    }

    public static PluginManager pluginManager() {
        PluginManager current = pluginManager;
        if (current == null) {
            throw new IllegalStateException("Aerogel runtime is not installed");
        }
        return current;
    }
}
