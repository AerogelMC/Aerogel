package dev.aerogel.loader.plugin;

import dev.aerogel.loader.util.Hashing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Immutable startup copies used by Mixin and the shared transforming class loader. */
public final class PluginSnapshots {
    private PluginSnapshots() {
    }

    public static List<PluginDescriptor> stage(List<PluginDescriptor> plugins, Path serverDirectory)
        throws IOException {
        List<PluginDescriptor> result = new ArrayList<>(plugins.size());
        for (PluginDescriptor plugin : plugins) result.add(stage(plugin, serverDirectory));
        return List.copyOf(result);
    }

    private static PluginDescriptor stage(PluginDescriptor plugin, Path serverDirectory) throws IOException {
        String hash = Hashing.sha256(plugin.jar());
        Path target = serverDirectory.resolve(".aerogel").resolve("plugin-runtime")
            .resolve(plugin.id()).resolve(hash + ".jar").toAbsolutePath().normalize();
        if (!valid(target, hash)) {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), plugin.id() + "-", ".tmp");
            boolean installed = false;
            try {
                Files.copy(plugin.jar(), temporary, StandardCopyOption.REPLACE_EXISTING);
                if (!Hashing.sha256(temporary).equals(hash) || !validJar(temporary)) {
                    throw new IOException("Plugin changed while it was being staged: " + plugin.id());
                }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                installed = true;
            } finally {
                if (!installed) Files.deleteIfExists(temporary);
            }
        }
        return new PluginDescriptor(target, plugin.id(), plugin.version(), plugin.name(), plugin.minecraft(),
            plugin.entrypoints(), plugin.mixins(), plugin.dependencies());
    }

    private static boolean valid(Path jar, String hash) {
        try { return Files.isRegularFile(jar) && Hashing.sha256(jar).equals(hash) && validJar(jar); }
        catch (IOException ignored) { return false; }
    }

    private static boolean validJar(Path jar) {
        try (JarFile file = new JarFile(jar.toFile(), false)) {
            return file.getJarEntry(PluginDescriptor.METADATA_PATH) != null;
        } catch (IOException exception) {
            return false;
        }
    }
}
