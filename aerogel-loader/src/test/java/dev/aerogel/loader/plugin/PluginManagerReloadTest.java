package dev.aerogel.loader.plugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.AerogelEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerReloadTest {
    @TempDir
    Path serverDirectory;

    @BeforeEach
    void resetCounters() {
        ReloadablePlugin.loads = 0;
        ReloadablePlugin.unloads = 0;
        ReloadablePlugin.events = 0;
    }

    @Test
    void reloadsOnePluginAndAllPluginsThroughTheLifecycleApi() throws Exception {
        Path pluginJar = serverDirectory.resolve("test.jar");
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(pluginJar))) {
            // The entrypoint class is supplied by the test class path; the scanner still receives a real plugin JAR.
            output.flush();
        }
        PluginDescriptor descriptor = new PluginDescriptor(
            pluginJar, "test_plugin", "1", "Test", ">=26.2",
            List.of(ReloadablePlugin.class.getName()), List.of("test.mixins.json"), Map.of());
        PluginManager manager = new PluginManager(
            serverDirectory, PluginManagerReloadTest.class.getClassLoader(), List.of(descriptor));

        manager.loadEntrypoints();
        manager.eventRegistry().post(new TestEvent());
        PluginManager.ReloadResult one = manager.reload("test_plugin").orElseThrow();
        manager.eventRegistry().post(new TestEvent());
        PluginManager.ReloadResult all = manager.reloadAll();
        manager.eventRegistry().post(new TestEvent());

        assertEquals(3, ReloadablePlugin.loads);
        assertEquals(2, ReloadablePlugin.unloads);
        assertEquals(3, ReloadablePlugin.events);
        assertTrue(one.successful());
        assertTrue(all.successful());
        assertEquals(List.of("test_plugin"), manager.pluginIds());
        assertEquals(List.of(new PluginManager.PluginInfo("test_plugin", "Test")), manager.pluginInfos());
        assertTrue(manager.hasMixins("test_plugin"));
        assertTrue(manager.reload("missing_plugin").isEmpty());
    }

    public static final class ReloadablePlugin implements AerogelPlugin {
        static int loads;
        static int unloads;
        static int events;

        @Override
        public void onLoad(PluginContext context) {
            loads++;
            context.events().listen(TestEvent.class, event -> events++);
        }

        @Override
        public void onUnload(PluginContext context) {
            unloads++;
        }
    }

    private static final class TestEvent implements AerogelEvent {
    }
}
