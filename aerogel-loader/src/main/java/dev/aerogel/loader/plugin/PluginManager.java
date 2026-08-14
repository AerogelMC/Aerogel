package dev.aerogel.loader.plugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.EventBus;
import dev.aerogel.loader.event.EventRegistry;
import dev.aerogel.loader.event.PluginEventScanner;
import dev.aerogel.loader.api.AerogelApiRuntime;
import dev.aerogel.loader.api.PluginApiScope;
import dev.aerogel.loader.mixin.MixinHotSwap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

public final class PluginManager {
    private static final AtomicLong LOAD_GENERATION = new AtomicLong();
    private final Path serverDirectory;
    private final ClassLoader classLoader;
    private final String minecraftVersion;
    private final PluginDiscovery discovery = new PluginDiscovery();
    private List<PluginDescriptor> plugins;
    private final EventRegistry eventRegistry;
    private final AerogelApiRuntime apiRuntime;
    private final PluginEventScanner eventScanner = new PluginEventScanner();
    private final Map<String, LoadedPlugin> loaded = new LinkedHashMap<>();
    private final Map<String, PluginDescriptor> disabled = new LinkedHashMap<>();

    public PluginManager(Path serverDirectory, ClassLoader classLoader, List<PluginDescriptor> plugins) {
        this(serverDirectory, classLoader, plugins, new EventRegistry(), new AerogelApiRuntime());
    }

    public PluginManager(
        Path serverDirectory,
        ClassLoader classLoader,
        List<PluginDescriptor> plugins,
        EventRegistry eventRegistry
    ) {
        this(serverDirectory, classLoader, plugins, eventRegistry, new AerogelApiRuntime());
    }

    public PluginManager(
        Path serverDirectory,
        ClassLoader classLoader,
        List<PluginDescriptor> plugins,
        EventRegistry eventRegistry,
        AerogelApiRuntime apiRuntime
    ) {
        this(serverDirectory, classLoader, plugins, eventRegistry, apiRuntime,
            System.getProperty("aerogel.minecraftVersion", "26.2"));
    }

    public PluginManager(
        Path serverDirectory,
        ClassLoader classLoader,
        List<PluginDescriptor> plugins,
        EventRegistry eventRegistry,
        AerogelApiRuntime apiRuntime,
        String minecraftVersion
    ) {
        this.serverDirectory = serverDirectory;
        this.classLoader = classLoader;
        this.plugins = List.copyOf(plugins);
        this.eventRegistry = eventRegistry;
        this.apiRuntime = apiRuntime;
        this.minecraftVersion = minecraftVersion;
    }

    public synchronized void loadEntrypoints() throws Exception {
        if (!loaded.isEmpty() || !disabled.isEmpty()) {
            throw new IllegalStateException("Plugin entrypoints are already loaded");
        }
        for (PluginDescriptor plugin : plugins) {
            String dependency = missingDependency(plugin);
            if (dependency != null) {
                PluginLoggers.create(plugin.id()).severe(
                    "Plugin disabled because dependency is not loaded: " + dependency);
                disabled.put(plugin.id(), plugin);
                continue;
            }
            try {
                loaded.put(plugin.id(), load(plugin));
                disabled.remove(plugin.id());
            } catch (Exception exception) {
                disabled.put(plugin.id(), plugin);
                PluginLoggers.create(plugin.id()).log(Level.SEVERE,
                    "Plugin failed during onLoad and was disabled; server startup will continue", exception);
            }
        }
    }


    public EventRegistry eventRegistry() {
        return eventRegistry;
    }

    public AerogelApiRuntime apiRuntime() {
        return apiRuntime;
    }

    public synchronized ReloadResult reloadAll() {
        List<PluginDescriptor> discovered;
        try {
            discovered = discoverPlugins();
        } catch (IOException exception) {
            return new ReloadResult(List.of(), List.of(), Map.of("scan", message(exception)));
        }
        plugins = discovered;
        Map<String, PluginDescriptor> byId = descriptorsById(discovered);
        disabled.keySet().removeIf(id -> !byId.containsKey(id));
        List<String> reloaded = new ArrayList<>();
        List<String> unloaded = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();

        List<LoadedPlugin> reverse = new ArrayList<>(loaded.values());
        Collections.reverse(reverse);
        for (LoadedPlugin plugin : reverse) {
            String pluginId = plugin.descriptor().id();
            if (byId.containsKey(pluginId)) continue;
            unload(plugin);
            loaded.remove(pluginId);
            unloaded.add(pluginId);
        }

        for (PluginDescriptor descriptor : discovered) {
            LoadedPlugin current = loaded.get(descriptor.id());
            String missingDependency = missingDependency(descriptor);
            if (missingDependency != null) {
                if (current != null) {
                    unload(current);
                    loaded.remove(descriptor.id());
                    unloaded.add(descriptor.id());
                }
                disabled.put(descriptor.id(), descriptor);
                failures.put(descriptor.id(), "Dependency is not loaded: " + missingDependency);
                continue;
            }
            String failure = current == null ? loadNew(descriptor) : reload(current, descriptor);
            if (failure == null) {
                reloaded.add(descriptor.id());
            } else {
                failures.put(descriptor.id(), failure);
            }
        }
        return new ReloadResult(reloaded, unloaded, failures);
    }

    public synchronized Optional<ReloadResult> reload(String pluginId) {
        String normalized = pluginId.toLowerCase(Locale.ROOT);
        List<PluginDescriptor> discovered;
        try {
            discovered = discoverPlugins();
        } catch (IOException exception) {
            return Optional.of(new ReloadResult(
                List.of(), List.of(), Map.of(normalized, message(exception))));
        }
        plugins = discovered;
        Map<String, PluginDescriptor> byId = descriptorsById(discovered);
        disabled.keySet().removeIf(id -> !byId.containsKey(id));
        PluginDescriptor descriptor = byId.get(normalized);
        LoadedPlugin current = loaded.get(normalized);
        if (descriptor == null && current == null) {
            return Optional.empty();
        }
        if (descriptor == null) {
            unload(current);
            loaded.remove(normalized);
            disabled.remove(normalized);
            return Optional.of(new ReloadResult(List.of(), List.of(normalized), Map.of()));
        }

        List<String> activated = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        Set<String> required = dependencyClosure(descriptor, byId);
        for (PluginDescriptor candidate : discovered) {
            if (!required.contains(candidate.id())) continue;
            LoadedPlugin loadedCandidate = loaded.get(candidate.id());
            String failure;
            if (candidate.id().equals(normalized) && loadedCandidate != null) {
                failure = reload(loadedCandidate, candidate);
            } else if (loadedCandidate == null) {
                failure = loadNew(candidate);
            } else {
                continue;
            }
            if (failure == null) {
                activated.add(candidate.id());
            } else {
                failures.put(candidate.id(), failure);
                break;
            }
        }
        return Optional.of(new ReloadResult(activated, List.of(), failures));
    }

    public synchronized List<String> pluginIds() {
        return List.copyOf(loaded.keySet());
    }

    public synchronized List<String> reloadablePluginIds() {
        try {
            return discoverPlugins().stream().map(PluginDescriptor::id).toList();
        } catch (IOException ignored) {
            return pluginIds();
        }
    }

    public synchronized void shutdown() {
        List<LoadedPlugin> reverse = new ArrayList<>(loaded.values());
        Collections.reverse(reverse);
        for (LoadedPlugin plugin : reverse) {
            unload(plugin);
        }
        loaded.clear();
        disabled.clear();
    }

    public synchronized List<PluginInfo> pluginInfos() {
        return plugins.stream()
            .filter(plugin -> loaded.containsKey(plugin.id()) || disabled.containsKey(plugin.id()))
            .map(plugin -> new PluginInfo(
                plugin.id(), plugin.name(), loaded.containsKey(plugin.id())))
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

    private String reload(LoadedPlugin plugin, PluginDescriptor descriptor) {
        String pluginId = plugin.descriptor().id();
        MixinHotSwap.Snapshot activeMixinState = plugin.mixinState();
        if (!descriptor.mixins().isEmpty()) {
            try {
                activeMixinState = MixinHotSwap.reload(descriptor, plugin.mixinState());
            } catch (Exception exception) {
                String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
                plugin.context().logger().warning(
                    "Mixin changes were not applied: " + detail + ". A server restart is required for those changes.");
            }
        }
        unload(plugin);
        loaded.remove(pluginId);
        try {
            loaded.put(pluginId, load(descriptor, activeMixinState));
            disabled.remove(pluginId);
            return null;
        } catch (Exception exception) {
            disabled.put(pluginId, descriptor);
            plugin.context().logger().severe("Reload failed: " + exception.getMessage());
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private String loadNew(PluginDescriptor descriptor) {
        String missingDependency = missingDependency(descriptor);
        if (missingDependency != null) {
            disabled.put(descriptor.id(), descriptor);
            return "Dependency is not loaded: " + missingDependency;
        }
        try {
            loaded.put(descriptor.id(), load(descriptor));
            disabled.remove(descriptor.id());
            return null;
        } catch (Exception exception) {
            disabled.put(descriptor.id(), descriptor);
            return message(exception);
        }
    }

    private String missingDependency(PluginDescriptor descriptor) {
        for (String dependency : descriptor.dependencies().keySet()) {
            if (!loaded.containsKey(dependency)) return dependency;
        }
        return null;
    }

    private List<PluginDescriptor> discoverPlugins() throws IOException {
        return discovery.discover(serverDirectory.resolve("plugins"), minecraftVersion);
    }

    private static Map<String, PluginDescriptor> descriptorsById(List<PluginDescriptor> descriptors) {
        Map<String, PluginDescriptor> byId = new LinkedHashMap<>();
        for (PluginDescriptor descriptor : descriptors) {
            byId.put(descriptor.id(), descriptor);
        }
        return byId;
    }

    private static Set<String> dependencyClosure(
        PluginDescriptor descriptor, Map<String, PluginDescriptor> descriptors
    ) {
        Set<String> result = new java.util.LinkedHashSet<>();
        collectDependencies(descriptor, descriptors, result);
        return result;
    }

    private static void collectDependencies(
        PluginDescriptor descriptor, Map<String, PluginDescriptor> descriptors, Set<String> result
    ) {
        if (!result.add(descriptor.id())) return;
        for (String dependency : descriptor.dependencies().keySet()) {
            collectDependencies(descriptors.get(dependency), descriptors, result);
        }
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private LoadedPlugin load(PluginDescriptor plugin) throws Exception {
        return load(plugin, MixinHotSwap.inspect(plugin));
    }

    private LoadedPlugin load(PluginDescriptor plugin, MixinHotSwap.Snapshot mixinState) throws Exception {
        Path dataDirectory = serverDirectory.resolve("plugins").resolve(plugin.id());
        Files.createDirectories(dataDirectory);
        Logger logger = PluginLoggers.create(plugin.id());
        EventRegistry.OwnedEventBus events = eventRegistry.owner(plugin.id(), logger);
        Path stagedJar = null;
        PluginClassLoader reloadableLoader = null;
        ClassLoader pluginLoader = classLoader;
        PluginDescriptor runtimeDescriptor = plugin;
        PluginApiScope api = null;
        PluginContext context = null;
        List<AerogelPlugin> instances = new ArrayList<>();
        Map<String, Object> instancesByClass = new LinkedHashMap<>();
        try {
            stagedJar = stage(plugin);
            List<PluginClassLoader> dependencyLoaders = plugin.dependencies().keySet().stream()
                .map(loaded::get)
                .filter(java.util.Objects::nonNull)
                .map(LoadedPlugin::classLoader)
                .toList();
            reloadableLoader = new PluginClassLoader(
                stagedJar.toUri().toURL(), classLoader, dependencyLoaders);
            pluginLoader = reloadableLoader;
            runtimeDescriptor = withJar(plugin, stagedJar);
            api = apiRuntime.openScope(plugin.id(), logger, pluginLoader, dataDirectory);
            context = new Context(
                plugin.id(), plugin.version(), serverDirectory, dataDirectory, logger, events, api
            );
            for (String entrypoint : plugin.entrypoints()) {
                Class<?> type = Class.forName(entrypoint, true, pluginLoader);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (!(instance instanceof AerogelPlugin aerogelPlugin)) {
                    throw new IllegalStateException(entrypoint + " must implement " + AerogelPlugin.class.getName());
                }
                aerogelPlugin.onLoad(context);
                instances.add(aerogelPlugin);
                instancesByClass.put(type.getName(), instance);
            }
            eventScanner.register(runtimeDescriptor, pluginLoader, context, events, instancesByClass);
            return new LoadedPlugin(
                plugin, context, List.copyOf(instances), events, api,
                reloadableLoader, stagedJar, mixinState);
        } catch (Throwable exception) {
            PluginFailures.rethrowFatal(exception);
            events.close();
            if (api != null) api.close();
            if (context != null) unloadReverse(instances, context);
            closeLoader(reloadableLoader, stagedJar, logger);
            if (exception instanceof Exception checked) throw checked;
            throw new IllegalStateException("Plugin linkage failed: " + plugin.id(), exception);
        }
    }

    private static void unload(LoadedPlugin plugin) {
        plugin.events().close();
        unloadReverse(plugin.instances(), plugin.context());
        plugin.api().close();
        closeLoader(plugin.classLoader(), plugin.stagedJar(), plugin.context().logger());
    }

    private Path stage(PluginDescriptor plugin) throws java.io.IOException {
        Path directory = serverDirectory.resolve(".aerogel").resolve("plugin-cache").resolve(plugin.id());
        Files.createDirectories(directory);
        Path staged = directory.resolve(plugin.id() + "-" + LOAD_GENERATION.incrementAndGet() + ".jar");
        Files.copy(plugin.jar(), staged, StandardCopyOption.REPLACE_EXISTING);
        return staged;
    }

    private static PluginDescriptor withJar(PluginDescriptor plugin, Path jar) {
        return new PluginDescriptor(
            jar, plugin.id(), plugin.version(), plugin.name(), plugin.minecraft(),
            plugin.entrypoints(), plugin.mixins(), plugin.dependencies());
    }

    private static void closeLoader(PluginClassLoader loader, Path stagedJar, Logger logger) {
        if (loader != null) {
            try { loader.close(); }
            catch (java.io.IOException exception) {
                logger.warning("Could not close plugin class loader: " + exception.getMessage());
            }
        }
        if (stagedJar != null) {
            try { Files.deleteIfExists(stagedJar); }
            catch (java.io.IOException exception) {
                logger.fine("Could not remove staged plugin JAR yet: " + exception.getMessage());
            }
        }
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

    public record ReloadResult(List<String> reloaded, List<String> unloaded, Map<String, String> failures) {
        public ReloadResult {
            reloaded = List.copyOf(reloaded);
            unloaded = List.copyOf(unloaded);
            failures = Map.copyOf(failures);
        }

        public boolean successful() {
            return failures.isEmpty();
        }
    }

    public record PluginInfo(String id, String name, boolean enabled) {
    }

    private record LoadedPlugin(
        PluginDescriptor descriptor,
        PluginContext context,
        List<AerogelPlugin> instances,
        EventRegistry.OwnedEventBus events,
        PluginApiScope api,
        PluginClassLoader classLoader,
        Path stagedJar,
        MixinHotSwap.Snapshot mixinState
    ) {
    }

    private record Context(
        String pluginId,
        String pluginVersion,
        Path serverDirectory,
        Path dataDirectory,
        Logger logger,
        EventBus events,
        dev.aerogel.api.AerogelServer server
    ) implements PluginContext {
    }
}
