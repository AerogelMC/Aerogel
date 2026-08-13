package dev.aerogel.loader.event;

import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.AerogelEvent;
import dev.aerogel.api.event.EventHandler;
import dev.aerogel.api.event.EventPriority;
import dev.aerogel.loader.plugin.PluginDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginEventScannerTest {
    @TempDir
    Path directory;

    @Test
    void discoversAnnotatedClassWithoutManualRegistration() throws Exception {
        Listener.calls = 0;
        Path jar = directory.resolve("listener.jar");
        String resource = Listener.class.getName().replace('.', '/') + ".class";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(resource));
            output.write(input.readAllBytes());
            output.closeEntry();
        }
        PluginDescriptor plugin = new PluginDescriptor(jar, "listener", "1", "Listener", ">=26.2",
            List.of(), List.of(), Map.of());
        EventRegistry registry = new EventRegistry();
        EventRegistry.OwnedEventBus events = registry.owner("listener", Logger.getLogger("listener"));
        PluginContext context = new TestContext(events);

        int count = new PluginEventScanner().register(plugin, getClass().getClassLoader(), context,
            events, Map.of());
        registry.post(new TestEvent());

        assertEquals(1, count);
        assertEquals(1, Listener.calls);
    }

    public static final class Listener {
        static int calls;

        public Listener(PluginContext context) {
        }

        @EventHandler(priority = EventPriority.EARLY)
        private void receive(TestEvent event) {
            calls++;
        }
    }

    public static final class TestEvent implements AerogelEvent {
    }

    private record TestContext(dev.aerogel.api.event.EventBus events) implements PluginContext {
        @Override public String pluginId() { return "listener"; }
        @Override public String pluginVersion() { return "1"; }
        @Override public Path serverDirectory() { return Path.of("."); }
        @Override public Path dataDirectory() { return Path.of("."); }
        @Override public Logger logger() { return Logger.getLogger("listener"); }
    }
}
