package dev.aerogel.loader.runtime;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformingClassLoaderTest {
    @Test
    void mixinBytecodeProviderCanReadAerogelCoreMixinFromParent() throws Exception {
        try (TransformingClassLoader loader = new TransformingClassLoader(
            new URL[0], TransformingClassLoaderTest.class.getClassLoader())) {
            byte[] bytes = loader.classBytes(
                "dev.aerogel.loader.mixin.core.MinecraftServerBrandMixin", false);

            assertTrue(bytes.length > 4);
            assertTrue(bytes[0] == (byte) 0xCA && bytes[1] == (byte) 0xFE
                && bytes[2] == (byte) 0xBA && bytes[3] == (byte) 0xBE);
        }
    }
}
