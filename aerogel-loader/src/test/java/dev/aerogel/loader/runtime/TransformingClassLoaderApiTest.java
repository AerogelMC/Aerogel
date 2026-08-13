package dev.aerogel.loader.runtime;

import dev.aerogel.api.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class TransformingClassLoaderApiTest {
    @Test
    void minecraftAndPublicApiTypesShareTheTransformingLoader() throws Exception {
        URL loaderClasses = ServerRuntime.class.getProtectionDomain().getCodeSource().getLocation();
        URL apiClasses = BlockBreakEvent.class.getProtectionDomain().getCodeSource().getLocation();
        URL minecraftStubs = Path.of("..", "aerogel-api", "build", "classes", "java", "minecraftStubs")
            .toAbsolutePath().normalize().toUri().toURL();

        try (TransformingClassLoader loader = new TransformingClassLoader(
            new URL[]{loaderClasses, apiClasses, minecraftStubs}, getClass().getClassLoader())) {
            Class<?> eventType = loader.loadClass(BlockBreakEvent.class.getName());
            Class<?> playerType = eventType.getMethod("player").getReturnType();

            assertNotSame(BlockBreakEvent.class, eventType);
            assertSame(loader, eventType.getClassLoader());
            assertSame(loader, playerType.getClassLoader());
            assertEquals("net.minecraft.server.level.ServerPlayer", playerType.getName());
        }
    }
}
