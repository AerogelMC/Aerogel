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
        assertEquals(List.of(new PluginManager.PluginInfo("test_plugin", "Test", true)), manager.pluginInfos());
        assertFalse(manager.hasMixins("test_plugin"));
        assertTrue(manager.reload("missing_plugin").isEmpty());
        assertFalse(Files.exists(plugins.resolve("test_plugin")),
            "A plugin data directory must stay absent until the plugin stores data");
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

    @Test
    void unchangedStartupMixinDoesNotRequestRestartOnReload() throws Exception {
        Path plugins = serverDirectory.resolve("plugins");
        Path pluginJar = plugins.resolve("mixin.jar");
        writeMixinPlugin(pluginJar, "mixin_plugin");
        List<PluginDescriptor> descriptors = new PluginDiscovery().discover(plugins, "26.2");
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), descriptors);
        manager.loadEntrypoints();

        PluginManager.ReloadResult result = manager.reload("mixin_plugin").orElseThrow();

        assertTrue(result.successful());
        assertEquals(List.of(), result.mixinRestartRequired());
    }

    @Test
    void runtimeAddedMixinKeepsRequestingRestartUntilItIsRemoved() throws Exception {
        Path plugins = serverDirectory.resolve("plugins");
        Files.createDirectories(plugins);
        Path pluginJar = plugins.resolve("mixin.jar");
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), List.of());
        manager.loadEntrypoints();

        writeMixinPlugin(pluginJar, "mixin_plugin");
        PluginManager.ReloadResult added = manager.reloadAll();
        PluginManager.ReloadResult stillPending = manager.reloadAll();
        writePlugin(pluginJar, "mixin_plugin", "Mixin", false);
        PluginManager.ReloadResult removedBeforeRestart = manager.reloadAll();

        assertEquals(List.of("mixin_plugin"), added.mixinRestartRequired());
        assertEquals(List.of("mixin_plugin"), stillPending.mixinRestartRequired());
        assertEquals(List.of(), removedBeforeRestart.mixinRestartRequired());
    }

    @Test
    void onePluginInitializationFailureDoesNotStopOtherPlugins() throws Exception {
        Path plugins = serverDirectory.resolve("plugins");
        Path broken = plugins.resolve("broken.jar");
        writePluginWithEntrypoint(broken, "broken_plugin", "Broken", FailingLoadPlugin.class);
        Path healthy = plugins.resolve("healthy.jar");
        writePlugin(healthy, "healthy_plugin", "Healthy", true);
        List<PluginDescriptor> descriptors = new PluginDiscovery().discover(plugins, "26.2");
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), descriptors);

        manager.loadEntrypoints();

        assertEquals(List.of("healthy_plugin"), manager.pluginIds());
        assertEquals(List.of(
            new PluginManager.PluginInfo("broken_plugin", "Broken", false),
            new PluginManager.PluginInfo("healthy_plugin", "Healthy", true)
        ), manager.pluginInfos());
        assertEquals("1", System.getProperty(ReloadFixturePlugin.LOADS));
    }

    private static void writePlugin(Path path, String id, String name, boolean entrypoint) throws Exception {
        writePluginWithEntrypoint(path, id, name, entrypoint ? ReloadFixturePlugin.class : null);
    }

    private static void writePluginWithEntrypoint(
        Path path, String id, String name, Class<?> entrypoint
    ) throws Exception {
        Files.createDirectories(path.getParent());
        String entrypointName = entrypoint == null ? null : entrypoint.getName();
        String metadata = """
            {
              "schemaVersion": 1,
              "id": "%s",
              "name": "%s",
              "version": "1",
              "minecraft": ">=26.2",
              "entrypoints": %s
            }
            """.formatted(id, name, entrypoint == null ? "[]" : "[\"" + entrypointName + "\"]");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(PluginDescriptor.METADATA_PATH));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            if (entrypoint != null) {
                String classPath = entrypointName.replace('.', '/') + ".class";
                output.putNextEntry(new JarEntry(classPath));
                try (InputStream input = entrypoint.getClassLoader().getResourceAsStream(classPath)) {
                    if (input == null) throw new IllegalStateException("Missing test fixture class " + classPath);
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
    }

    private static void writeMixinPlugin(Path path, String id) throws Exception {
        Files.createDirectories(path.getParent());
        String metadata = """
            {
              "schemaVersion": 1,
              "id": "%s",
              "name": "Mixin",
              "version": "1",
              "minecraft": ">=26.2",
              "entrypoints": [],
              "mixins": ["test.mixins.json"]
            }
            """.formatted(id);
        String config = "{\"package\":\"test\",\"mixins\":[\"Fixture\"]}";
        String fixturePath = ReloadFixturePlugin.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(PluginDescriptor.METADATA_PATH));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("test.mixins.json"));
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("test/Fixture.class"));
            try (InputStream input = ReloadFixturePlugin.class.getClassLoader().getResourceAsStream(fixturePath)) {
                if (input == null) throw new IllegalStateException("Missing test fixture " + fixturePath);
                input.transferTo(output);
            }
            output.closeEntry();
        }
    }

}
