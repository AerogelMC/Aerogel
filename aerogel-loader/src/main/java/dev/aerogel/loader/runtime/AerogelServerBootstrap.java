package dev.aerogel.loader.runtime;

import dev.aerogel.loader.BuildInfo;
import dev.aerogel.loader.install.ServerBundle;
import dev.aerogel.loader.mixin.MixinBootstrapper;
import dev.aerogel.loader.plugin.PluginDescriptor;
import dev.aerogel.loader.plugin.PluginDiscovery;
import dev.aerogel.loader.plugin.PluginSnapshots;
import dev.aerogel.loader.util.Hashing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AerogelServerBootstrap {
    private AerogelServerBootstrap() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable exception) {
            printFatal(exception);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Throwable {
        Path serverDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path serverJar = Path.of(requiredProperty("aerogel.serverJar")).toAbsolutePath().normalize();
        String minecraftVersion = requiredProperty("aerogel.minecraftVersion");
        BuildInfo build = BuildInfo.current();
        System.out.printf("[Aerogel] %s | Minecraft %s | Mixin %s%n",
            build.version(), minecraftVersion, build.mixinVersion());
        String bundleKey = Hashing.sha1(serverJar).substring(0, 16);
        ServerBundle bundle = ServerBundle.extract(
            serverJar,
            serverDirectory.resolve(".aerogel").resolve("bundle").resolve(minecraftVersion + "-" + bundleKey)
        );
        List<PluginDescriptor> plugins = PluginSnapshots.stage(new PluginDiscovery().discover(
            serverDirectory.resolve("plugins"), minecraftVersion
        ), serverDirectory);
        List<URL> urls = new ArrayList<>();
        urls.add(AerogelServerBootstrap.class.getProtectionDomain().getCodeSource().getLocation());
        for (Path artifact : bundle.classPath()) {
            urls.add(artifact.toUri().toURL());
        }
        for (PluginDescriptor plugin : plugins) {
            urls.add(plugin.jar().toUri().toURL());
        }

        // The dedicated server continues on its own non-daemon thread after main may return.
        // Keep this process-lifetime loader open so late resource/class loads remain valid.
        TransformingClassLoader target = new TransformingClassLoader(
            urls.toArray(URL[]::new), AerogelServerBootstrap.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(target);
        ConsoleLogging.configure(target);
        for (PluginDescriptor plugin : plugins) {
            System.out.printf("[Aerogel] Plugin %s %s (%s)%n",
                plugin.id(), plugin.version(), plugin.jar().getFileName());
        }
        MixinBootstrapper.initialize(target, plugins);
        invokeRuntime(target, serverDirectory, minecraftVersion, bundle.mainClass(), args);
    }

    private static void printFatal(Throwable exception) {
        if (!ConsoleLogging.ansiEnabled()) {
            exception.printStackTrace(System.err);
            return;
        }
        synchronized (System.err) {
            System.err.print("\u001B[31m");
            try {
                exception.printStackTrace(System.err);
            } finally {
                System.err.print("\u001B[0m");
                System.err.flush();
            }
        }
    }

    private static void invokeRuntime(
        ClassLoader target, Path serverDirectory, String minecraftVersion, String mainClass, String[] args
    ) throws Throwable {
        Class<?> type = Class.forName("dev.aerogel.loader.runtime.ServerRuntime", true, target);
        Method method = type.getMethod(
            "run", Path.class, String.class, String.class, String[].class);
        try {
            method.invoke(null, serverDirectory, minecraftVersion, mainClass, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property " + name);
        }
        return value;
    }
}
