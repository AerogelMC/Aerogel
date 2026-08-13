package dev.aerogel.loader.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginDiscoveryTest {
    @TempDir
    Path directory;

    @Test
    void ordersDependenciesBeforeDependants() throws Exception {
        plugin("feature.jar", "feature", "1.0.0", "\"base\": \">=2.0.0\"");
        plugin("base.jar", "base", "2.1.0", "");

        List<PluginDescriptor> plugins = new PluginDiscovery().discover(directory, "26.2");

        assertEquals(List.of("base", "feature"), plugins.stream().map(PluginDescriptor::id).toList());
    }

    @Test
    void rejectsDependencyCycles() throws Exception {
        plugin("a.jar", "plugin_a", "1.0.0", "\"plugin_b\": \"*\"");
        plugin("b.jar", "plugin_b", "1.0.0", "\"plugin_a\": \"*\"");

        assertThrows(IOException.class, () -> new PluginDiscovery().discover(directory, "26.2"));
    }

    private void plugin(String file, String id, String version, String dependencies) throws IOException {
        String json = """
            {
              "schemaVersion": 1,
              "id": "%s",
              "version": "%s",
              "minecraft": ">=26.2",
              "mixins": ["%s.mixins.json"],
              "depends": {%s}
            }
            """.formatted(id, version, id, dependencies);
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(directory.resolve(file)))) {
            entry(output, PluginDescriptor.METADATA_PATH, json);
            entry(output, id + ".mixins.json", "{\"package\":\"example\",\"mixins\":[]}");
        }
    }

    private static void entry(JarOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
