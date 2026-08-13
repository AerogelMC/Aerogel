package dev.aerogel.loader.plugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.EventBus;
import dev.aerogel.loader.event.EventRegistry;
import dev.aerogel.loader.event.PluginEventScanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class PluginManager {
    private final Path serverDirectory;
    private final ClassLoader classLoader;
    private final List<PluginDescriptor> plugins;
    private final EventRegistry eventRegistry;
    private final PluginEventScanner eventScanner = new PluginEventScanner();
    private final Map<String, LoadedPlugin> loaded = new LinkedHashMap<>();

    public PluginManager(Path serverDirectory, ClassLoader classLoader, List<PluginDescriptor> plugins) {
        this(serverDirectory, classLoader, plugins, new EventRegistry());
    }

    public PluginManager(
        Path serverDirectory,
        ClassLoader classLoader,
        List<PluginDescriptor> plugins,
        EventRegistry eventRegistry
    ) {
        this.serverDirectory = serverDirectory;
        this.classLoader = classLoader;
        this.plugins = plugins;
        this.eventRegistry = eventRegistry;
    }

    public synchronized void loadEntrypoints() throws Exception {
        if (!loaded.isEmpty()) {
            throw new IllegalStateException("Plugin entrypoints are already loaded");
        }
        for (PluginDescriptor plugin : plugins) {
            loaded.put(plugin.id(), load(plugin));
        }
    }

    public EventRegistry eventRegistry() {
        return eventRegistry;
    }

    public synchronized ReloadResult reloadAll() {
        List<String> reloaded = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (String pluginId : List.copyOf(loaded.keySet())) {
            LoadedPlugin plugin = loaded.get(pluginId);
            String failure = reload(plugin);
            if (failure == null) {
                reloaded.add(pluginId);
            } else {
                failures.put(pluginId, failure);
            }
        }
        return new ReloadResult(reloaded, failures);
    }

    public synchronized Optional<ReloadResult> reload(String pluginId) {
        String normalized = pluginId.toLowerCase(Locale.ROOT);
        LoadedPlugin plugin = loaded.get(normalized);
        if (plugin == null) {
            return Optional.empty();
        }
        String failure = reload(plugin);
        return Optional.of(failure == null
            ? new ReloadResult(List.of(normalized), Map.of())
            : new ReloadResult(List.of(), Map.of(normalized, failure)));
    }

    public synchronized List<String> pluginIds() {
        return List.copyOf(loaded.keySet());
    }

    public synchronized List<PluginInfo> pluginInfos() {
        return loaded.values().stream()
            .map(plugin -> new PluginInfo(plugin.descriptor().id(), plugin.descriptor().name()))
            .toList();
    }

    public synchronized boolean hasMixins(String pluginId) {
        LoadedPlugin plugin = loaded.get(pluginId.toLowerCase(Locale.ROOT));
        return plugin != null && !plugin.descriptor().mixins().isEmpty();
    }

    public synchronized List<String> mixinPluginIds() {
        return loaded.values().stream()
            .filter(plugin -> !plugin.descriptor().mixins().isEmpty())
            .map(plugin -> plugin.descriptor().id())
            .toList();
    }

    private String reload(LoadedPlugin plugin) {
        plugin.events().close();
        Logger logger = plugin.context().logger();
        EventRegistry.OwnedEventBus events = eventRegistry.owner(plugin.descriptor().id(), logger);
        PluginContext context = new Context(
            plugin.descriptor().id(), plugin.descriptor().version(), serverDirectory,
            plugin.context().dataDirectory(), logger, events
        );
        try {
            Map<String, Object> instancesByClass = new LinkedHashMap<>();
            for (AerogelPlugin instance : plugin.instances()) {
                instance.onReload(context);
                instancesByClass.put(instance.getClass().getName(), instance);
            }
            eventScanner.register(plugin.descriptor(), classLoader, context, events, instancesByClass);
            loaded.put(plugin.descriptor().id(),
                new LoadedPlugin(plugin.descriptor(), context, plugin.instances(), events));
            return null;
        } catch (Exception exception) {
            events.close();
            loaded.remove(plugin.descriptor().id());
            logger.severe("Reload failed: " + exception.getMessage());
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private LoadedPlugin load(PluginDescriptor plugin) throws Exception {
        Path dataDirectory = serverDirectory.resolve("plugins").resolve(plugin.id());
        Files.createDirectories(dataDirectory);
        Logger logger = Logger.getLogger("Aerogel/" + plugin.id());
        EventRegistry.OwnedEventBus events = eventRegistry.owner(plugin.id(), logger);
        PluginContext context = new Context(
            plugin.id(), plugin.version(), serverDirectory, dataDirectory, logger, events
        );
        List<AerogelPlugin> instances = new ArrayList<>();
        Map<String, Object> instancesByClass = new LinkedHashMap<>();
        try {
            for (String entrypoint : plugin.entrypoints()) {
                Class<?> type = Class.forName(entrypoint, true, classLoader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (!(instance instanceof AerogelPlugin aerogelPlugin)) {
                    throw new IllegalStateException(entrypoint + " must implement " + AerogelPlugin.class.getName());
                }
                aerogelPlugin.onLoad(context);
                instances.add(aerogelPlugin);
                instancesByClass.put(type.getName(), instance);
            }
            eventScanner.register(plugin, classLoader, context, events, instancesByClass);
            return new LoadedPlugin(plugin, context, List.copyOf(instances), events);
        } catch (Exception exception) {
            events.close();
            unloadReverse(instances, context);
            throw exception;
        }
    }

    private static void unload(LoadedPlugin plugin) {
        plugin.events().close();
        unloadReverse(plugin.instances(), plugin.context());
    }

    private static void unloadReverse(List<AerogelPlugin> instances, PluginContext context) {
        List<AerogelPlugin> reverse = new ArrayList<>(instances);
        Collections.reverse(reverse);
        for (AerogelPlugin instance : reverse) {
            try {
                instance.onUnload(context);
            } catch (Exception exception) {
                context.logger().warning("Plugin cleanup failed: " + exception.getMessage());
            }
        }
    }

    public record ReloadResult(List<String> reloaded, Map<String, String> failures) {
        public ReloadResult {
            reloaded = List.copyOf(reloaded);
            failures = Map.copyOf(failures);
        }

        public boolean successful() {
            return failures.isEmpty();
        }
    }

    public record PluginInfo(String id, String name) {
    }

    private record LoadedPlugin(
        PluginDescriptor descriptor,
        PluginContext context,
        List<AerogelPlugin> instances,
        EventRegistry.OwnedEventBus events
    ) {
    }

    private record Context(
        String pluginId,
        String pluginVersion,
        Path serverDirectory,
        Path dataDirectory,
        Logger logger,
        EventBus events
    ) implements PluginContext {
    }
}
