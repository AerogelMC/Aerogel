package dev.aerogel.loader.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchOptionsTest {
    @Test
    void separatesAerogelAndMinecraftArguments() {
        LaunchOptions options = LaunchOptions.parse(new String[] {
            "run", "--game-dir=run/test", "--jvm-arg=-Xmx2G", "--", "--port", "25566"
        }, "26.2");

        assertEquals(LaunchOptions.Command.RUN, options.command());
        assertEquals("26.2", options.minecraftVersion());
        assertEquals(java.util.List.of("-Xmx2G"), options.jvmArguments());
        assertEquals(java.util.List.of("--port", "25566"), options.serverArguments());
        assertTrue(options.serverJar().endsWith(java.nio.file.Path.of("runtime", "26.2", "server.jar")));
    }
}
