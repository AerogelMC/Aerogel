package dev.aerogel.loader.plugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

public final class PluginManager {
    private final Path serverDirectory;
    private final ClassLoader classLoader;
    private final List<PluginDescriptor> plugins;

    public PluginManager(Path serverDirectory, ClassLoader classLoader, List<PluginDescriptor> plugins) {
        this.serverDirectory = serverDirectory;
        this.classLoader = classLoader;
        this.plugins = plugins;
    }

    public void loadEntrypoints() throws Exception {
        for (PluginDescriptor plugin : plugins) {
            Path dataDirectory = serverDirectory.resolve("plugins").resolve(plugin.id());
            Files.createDirectories(dataDirectory);
            PluginContext context = new Context(
                plugin.id(), plugin.version(), serverDirectory, dataDirectory, Logger.getLogger("Aerogel/" + plugin.id())
            );
            for (String entrypoint : plugin.entrypoints()) {
                Class<?> type = Class.forName(entrypoint, true, classLoader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (!(instance instanceof AerogelPlugin aerogelPlugin)) {
                    throw new IllegalStateException(entrypoint + " must implement " + AerogelPlugin.class.getName());
                }
                aerogelPlugin.onLoad(context);
            }
        }
    }

    private record Context(
        String pluginId,
        String pluginVersion,
        Path serverDirectory,
        Path dataDirectory,
        Logger logger
    ) implements PluginContext {
    }
}
