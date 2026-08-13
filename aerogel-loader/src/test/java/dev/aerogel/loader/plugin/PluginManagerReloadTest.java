package dev.aerogel.loader.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerReloadTest {
    @TempDir
    Path serverDirectory;

    @BeforeEach
    void resetCounters() {
        System.setProperty(ReloadFixturePlugin.LOADS, "0");
        System.setProperty(ReloadFixturePlugin.UNLOADS, "0");
    }

    @Test
    void reloadsPluginCodeThroughFreshClassLoaders() throws Exception {
        Path plugins = serverDirectory.resolve("plugins");
        Path pluginJar = plugins.resolve("test.jar");
        writePlugin(pluginJar, "test_plugin", "Test", true);
        List<PluginDescriptor> descriptors = new PluginDiscovery().discover(plugins, "26.2");
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), descriptors);

        manager.loadEntrypoints();
        PluginManager.ReloadResult one = manager.reload("test_plugin").orElseThrow();
        PluginManager.ReloadResult all = manager.reloadAll();

        assertEquals("3", System.getProperty(ReloadFixturePlugin.LOADS));
        assertEquals("2", System.getProperty(ReloadFixturePlugin.UNLOADS));
        assertTrue(one.successful());
        assertTrue(all.successful());
        assertEquals(List.of("test_plugin"), manager.pluginIds());
        assertEquals(List.of(new PluginManager.PluginInfo("test_plugin", "Test")), manager.pluginInfos());
        assertFalse(manager.hasMixins("test_plugin"));
        assertTrue(manager.reload("missing_plugin").isEmpty());
    }

    @Test
    void reloadAllLoadsNewJarsAndUnloadsRemovedJars() throws Exception {
        Path plugins = serverDirectory.resolve("plugins");
        Files.createDirectories(plugins);
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), List.of());
        manager.loadEntrypoints();

        Path addedJar = plugins.resolve("added.jar");
        writePlugin(addedJar, "added_plugin", "Added", false);
        PluginManager.ReloadResult added = manager.reloadAll();

        assertTrue(added.successful());
        assertEquals(List.of("added_plugin"), added.reloaded());
        assertEquals(List.of(), added.unloaded());
        assertEquals(List.of("added_plugin"), manager.pluginIds());

        Files.delete(addedJar);
        PluginManager.ReloadResult removed = manager.reloadAll();

        assertTrue(removed.successful());
        assertEquals(List.of(), removed.reloaded());
        assertEquals(List.of("added_plugin"), removed.unloaded());
        assertEquals(List.of(), manager.pluginIds());
    }

    @Test
    void targetedReloadCanLoadANewPlugin() throws Exception {
        Path plugins = serverDirectory.resolve("plugins");
        Files.createDirectories(plugins);
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), List.of());
        manager.loadEntrypoints();

        writePlugin(plugins.resolve("targeted.jar"), "targeted_plugin", "Targeted", false);
        PluginManager.ReloadResult result = manager.reload("targeted_plugin").orElseThrow();

        assertTrue(result.successful());
        assertEquals(List.of("targeted_plugin"), result.reloaded());
        assertEquals(List.of("targeted_plugin"), manager.pluginIds());
    }

    private static void writePlugin(Path path, String id, String name, boolean entrypoint) throws Exception {
        Files.createDirectories(path.getParent());
        String entrypointName = ReloadFixturePlugin.class.getName();
        String metadata = """
            {
              "schemaVersion": 1,
              "id": "%s",
              "name": "%s",
              "version": "1",
              "minecraft": ">=26.2",
              "entrypoints": %s
            }
            """.formatted(id, name, entrypoint ? "[\"" + entrypointName + "\"]" : "[]");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(PluginDescriptor.METADATA_PATH));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            if (entrypoint) {
                String classPath = entrypointName.replace('.', '/') + ".class";
                output.putNextEntry(new JarEntry(classPath));
                try (InputStream input = ReloadFixturePlugin.class.getClassLoader().getResourceAsStream(classPath)) {
                    if (input == null) throw new IllegalStateException("Missing test fixture class " + classPath);
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
    }
}
