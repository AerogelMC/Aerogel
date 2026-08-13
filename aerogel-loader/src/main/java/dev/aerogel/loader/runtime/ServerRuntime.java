package dev.aerogel.loader.runtime;

import dev.aerogel.loader.api.AerogelApiRuntime;
import dev.aerogel.loader.event.AerogelEvents;
import dev.aerogel.loader.event.EventRegistry;
import dev.aerogel.loader.plugin.PluginDescriptor;
import dev.aerogel.loader.plugin.PluginDiscovery;
import dev.aerogel.loader.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

/** Runs inside the transforming class loader shared by Minecraft and plugins. */
public final class ServerRuntime {
    private ServerRuntime() {
    }

    public static void run(
        Path serverDirectory, String minecraftVersion, String mainClass, String[] args
    ) throws Throwable {
        ClassLoader target = ServerRuntime.class.getClassLoader();
        List<PluginDescriptor> plugins = new PluginDiscovery().discover(
            serverDirectory.resolve("plugins"), minecraftVersion);
        AerogelApiRuntime apiRuntime = new AerogelApiRuntime();
        PluginManager pluginManager = new PluginManager(
            serverDirectory, target, plugins, new EventRegistry(), apiRuntime, minecraftVersion);
        AerogelEvents.install(pluginManager.eventRegistry());
        AerogelRuntime.install(pluginManager);
        invokeMain(target, mainClass, args);
    }

    private static void invokeMain(ClassLoader target, String mainClass, String[] args) throws Throwable {
        Class<?> type = Class.forName(mainClass, true, target);
        Method method = type.getMethod("main", String[].class);
        try {
            method.invoke(null, (Object) args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
