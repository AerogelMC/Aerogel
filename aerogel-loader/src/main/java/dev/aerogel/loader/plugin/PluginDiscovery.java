package dev.aerogel.loader.plugin;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PluginDiscovery {
    private static final int MAX_METADATA_BYTES = 1024 * 1024;

    public List<PluginDescriptor> discover(Path directory, String minecraftVersion) throws IOException {
        Files.createDirectories(directory);
        List<Path> jars;
        try (var stream = Files.list(directory)) {
            jars = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
        Map<String, PluginDescriptor> byId = new LinkedHashMap<>();
        for (Path jarPath : jars) {
            PluginDescriptor descriptor = read(jarPath);
            PluginDescriptor duplicate = byId.putIfAbsent(descriptor.id(), descriptor);
            if (duplicate != null) {
                throw new IOException("Duplicate plugin id '" + descriptor.id() + "': " + duplicate.jar() + " and " + jarPath);
            }
            if (!VersionConstraint.matches(minecraftVersion, descriptor.minecraft())) {
                throw new IOException("Plugin " + descriptor.id() + " requires Minecraft " + descriptor.minecraft()
                    + " but Aerogel is launching " + minecraftVersion);
            }
        }
        validateDependencies(byId);
        return topologicalOrder(byId);
    }

    private static PluginDescriptor read(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            JarEntry entry = jar.getJarEntry(PluginDescriptor.METADATA_PATH);
            if (entry == null) {
                throw new IOException("Not an Aerogel plugin (missing " + PluginDescriptor.METADATA_PATH + "): " + path);
            }
            if (entry.getSize() > MAX_METADATA_BYTES) {
                throw new IOException("Oversized plugin metadata in " + path);
            }
            PluginDescriptor descriptor;
            try (var input = jar.getInputStream(entry)) {
                byte[] metadata = input.readNBytes(MAX_METADATA_BYTES + 1);
                if (metadata.length > MAX_METADATA_BYTES) {
                    throw new IOException("Oversized plugin metadata in " + path);
                }
                descriptor = PluginDescriptor.parse(
                    path, new StringReader(new String(metadata, StandardCharsets.UTF_8))
                );
            }
            for (String mixin : descriptor.mixins()) {
                if (jar.getJarEntry(mixin) == null) {
                    throw new IOException("Plugin " + descriptor.id() + " is missing Mixin config " + mixin);
                }
            }
            for (String entrypoint : descriptor.entrypoints()) {
                if (jar.getJarEntry(entrypoint.replace('.', '/') + ".class") == null) {
                    throw new IOException("Plugin " + descriptor.id() + " is missing entrypoint " + entrypoint);
                }
            }
            return descriptor;
        }
    }

    private static void validateDependencies(Map<String, PluginDescriptor> plugins) throws IOException {
        for (PluginDescriptor plugin : plugins.values()) {
            for (Map.Entry<String, String> dependency : plugin.dependencies().entrySet()) {
                PluginDescriptor target = plugins.get(dependency.getKey());
                if (target == null) {
                    throw new IOException("Plugin " + plugin.id() + " requires missing plugin " + dependency.getKey());
                }
                if (!VersionConstraint.matches(target.version(), dependency.getValue())) {
                    throw new IOException("Plugin " + plugin.id() + " requires " + dependency.getKey() + " "
                        + dependency.getValue() + ", found " + target.version());
                }
            }
        }
    }

    private static List<PluginDescriptor> topologicalOrder(Map<String, PluginDescriptor> plugins) throws IOException {
        List<PluginDescriptor> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (PluginDescriptor plugin : plugins.values()) {
            visit(plugin, plugins, visiting, visited, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visit(PluginDescriptor plugin, Map<String, PluginDescriptor> plugins, Set<String> visiting,
                              Set<String> visited, List<PluginDescriptor> ordered) throws IOException {
        if (visited.contains(plugin.id())) {
            return;
        }
        if (!visiting.add(plugin.id())) {
            throw new IOException("Plugin dependency cycle contains " + plugin.id());
        }
        for (String dependency : plugin.dependencies().keySet()) {
            visit(plugins.get(dependency), plugins, visiting, visited, ordered);
        }
        visiting.remove(plugin.id());
        visited.add(plugin.id());
        ordered.add(plugin);
    }

    static final class VersionConstraint {
        private VersionConstraint() {
        }

        static boolean matches(String version, String constraint) {
            String trimmed = constraint.strip();
            if (trimmed.equals("*") || trimmed.isEmpty()) {
                return true;
            }
            if (trimmed.startsWith(">=")) {
                return compare(version, trimmed.substring(2).strip()) >= 0;
            }
            if (trimmed.startsWith(">")) {
                return compare(version, trimmed.substring(1).strip()) > 0;
            }
            if (trimmed.startsWith("<=")) {
                return compare(version, trimmed.substring(2).strip()) <= 0;
            }
            if (trimmed.startsWith("<")) {
                return compare(version, trimmed.substring(1).strip()) < 0;
            }
            return compare(version, trimmed.startsWith("=") ? trimmed.substring(1).strip() : trimmed) == 0;
        }

        private static int compare(String left, String right) {
            String[] a = left.split("[.+-]");
            String[] b = right.split("[.+-]");
            int size = Math.max(a.length, b.length);
            for (int i = 0; i < size; i++) {
                String x = i < a.length ? a[i] : "0";
                String y = i < b.length ? b[i] : "0";
                int result;
                try {
                    result = Integer.compare(Integer.parseInt(x), Integer.parseInt(y));
                } catch (NumberFormatException ignored) {
                    result = x.compareTo(y);
                }
                if (result != 0) {
                    return result;
                }
            }
            return 0;
        }
    }
}
