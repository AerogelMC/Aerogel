package dev.aerogel.loader.plugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/** Reloadable child-first namespace for one plugin, sharing Minecraft and Aerogel API types. */
final class PluginClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final String[] SHARED_PACKAGES = {
        "java.", "javax.", "jdk.", "sun.", "com.sun.",
        "dev.aerogel.api.", "dev.aerogel.loader.",
        "net.minecraft.", "com.mojang.",
        "org.spongepowered.", "org.objectweb.asm.",
        "org.slf4j.", "org.apache.logging."
    };
    private final List<PluginClassLoader> dependencies;

    PluginClassLoader(URL pluginJar, ClassLoader parent, List<PluginClassLoader> dependencies) {
        super(new URL[]{pluginJar}, parent);
        this.dependencies = List.copyOf(dependencies);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (shared(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = loadFromDependencies(name);
                    }
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    @Override
    public URL getResource(String name) {
        URL local = findResource(name);
        if (local != null) return local;
        for (PluginClassLoader dependency : dependencies) {
            URL resource = dependency.findDependencyResource(name);
            if (resource != null) return resource;
        }
        return super.getResource(name);
    }

    private Class<?> loadFromDependencies(String name) throws ClassNotFoundException {
        for (PluginClassLoader dependency : dependencies) {
            try {
                return dependency.loadDependencyClass(name);
            } catch (ClassNotFoundException ignored) {
                // Try the next declared dependency before delegating to the shared parent.
            }
        }
        return super.loadClass(name, false);
    }

    private Class<?> loadDependencyClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            try {
                return findClass(name);
            } catch (ClassNotFoundException ignored) {
                for (PluginClassLoader dependency : dependencies) {
                    try {
                        return dependency.loadDependencyClass(name);
                    } catch (ClassNotFoundException nested) {
                        // Continue through the declared dependency graph.
                    }
                }
                throw new ClassNotFoundException(name);
            }
        }
    }

    private URL findDependencyResource(String name) {
        URL local = findResource(name);
        if (local != null) return local;
        for (PluginClassLoader dependency : dependencies) {
            URL resource = dependency.findDependencyResource(name);
            if (resource != null) return resource;
        }
        return null;
    }

    private static boolean shared(String name) {
        for (String prefix : SHARED_PACKAGES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }
}
