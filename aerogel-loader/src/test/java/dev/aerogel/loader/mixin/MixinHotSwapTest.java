package dev.aerogel.loader.mixin;

import dev.aerogel.loader.plugin.PluginDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MixinHotSwapTest {
    @TempDir
    Path directory;

    @Test
    void unchangedMixinSkipsAgentAndJsonFormattingDoesNotCountAsAChange() throws Exception {
        byte[] mixinClass = classBytes();
        Path firstJar = directory.resolve("first.jar");
        Path secondJar = directory.resolve("second.jar");
        write(firstJar, """
            {"required":true,"package":"test","mixins":["Fixture"]}
            """, mixinClass);
        write(secondJar, """
            {
              "mixins": ["Fixture"],
              "package": "test",
              "required": true
            }
            """, mixinClass);

        MixinHotSwap.Snapshot first = MixinHotSwap.inspect(descriptor(firstJar));
        MixinHotSwap.Snapshot second = MixinHotSwap.inspect(descriptor(secondJar));

        assertEquals(first, second);
        assertEquals(second, MixinHotSwap.reload(descriptor(secondJar), first));
    }

    @Test
    void tracksMixinBytecodeSeparatelyFromItsConfiguration() throws Exception {
        byte[] original = classBytes();
        byte[] changed = java.util.Arrays.copyOf(original, original.length + 1);
        Path firstJar = directory.resolve("original.jar");
        Path secondJar = directory.resolve("changed.jar");
        String config = "{\"package\":\"test\",\"mixins\":[\"Fixture\"]}";
        write(firstJar, config, original);
        write(secondJar, config, changed);

        MixinHotSwap.Snapshot first = MixinHotSwap.inspect(descriptor(firstJar));
        MixinHotSwap.Snapshot second = MixinHotSwap.inspect(descriptor(secondJar));

        assertEquals(first.configurations(), second.configurations());
        assertNotEquals(first.classHashes(), second.classHashes());
    }

    private static PluginDescriptor descriptor(Path jar) {
        return new PluginDescriptor(jar, "test_plugin", "1", "Test", ">=26.2",
            List.of(), List.of("test.mixins.json"), Map.of());
    }

    private static byte[] classBytes() throws Exception {
        String path = MixinHotSwapTest.class.getName().replace('.', '/') + ".class";
        try (InputStream input = MixinHotSwapTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing test class " + path);
            return input.readAllBytes();
        }
    }

    private static void write(Path jar, String config, byte[] mixinClass) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("test.mixins.json"));
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("test/Fixture.class"));
            output.write(mixinClass);
            output.closeEntry();
        }
    }
}
