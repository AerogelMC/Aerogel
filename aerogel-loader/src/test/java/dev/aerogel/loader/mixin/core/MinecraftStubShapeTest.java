package dev.aerogel.loader.mixin.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftStubShapeTest {
    @Test
    void mutableComponentMatchesVanillasClassKind() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
            "net/minecraft/network/chat/MutableComponent.class")) {
            assertTrue(input != null, "Missing MutableComponent development stub");
            int access = new ClassReader(input).getAccess();
            assertFalse((access & Opcodes.ACC_INTERFACE) != 0);
            assertTrue((access & Opcodes.ACC_FINAL) != 0);
        }
    }
}
