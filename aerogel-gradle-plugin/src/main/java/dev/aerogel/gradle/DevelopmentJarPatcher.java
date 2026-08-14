package dev.aerogel.gradle;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Adds compile-only declarations for methods which Aerogel injects at runtime. */
final class DevelopmentJarPatcher {
    private static final String BORROWED_TASK_SCHEDULER = "net/minecraft/util/thread/TaskScheduler";
    private static final Map<String, List<InjectedMethod>> METHODS = methods();

    private DevelopmentJarPatcher() {
    }

    static void patch(Path classpath) throws IOException {
        try (var paths = Files.walk(classpath)) {
            for (Path jar : paths.filter(path -> path.toString().endsWith(".jar")).toList()) {
                patchJar(jar);
            }
        }
    }

    private static void patchJar(Path jar) throws IOException {
        boolean relevant;
        try (JarFile input = new JarFile(jar.toFile(), false)) {
            relevant = input.getJarEntry(BORROWED_TASK_SCHEDULER + ".class") != null
                || METHODS.keySet().stream().anyMatch(name -> input.getJarEntry(name + ".class") != null);
        }
        if (!relevant) return;

        Path temporary = Files.createTempFile(jar.getParent(), "aerogel-dev-", ".jar");
        boolean complete = false;
        try (JarFile input = new JarFile(jar.toFile(), false);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry source = entries.nextElement();
                String name = source.getName();
                if (signatureFile(name)) continue;
                JarEntry target = new JarEntry(name);
                target.setTime(source.getTime());
                output.putNextEntry(target);
                if (!source.isDirectory()) {
                    try (InputStream content = input.getInputStream(source)) {
                        byte[] bytes = content.readAllBytes();
                        String className = name.endsWith(".class")
                            ? name.substring(0, name.length() - 6) : null;
                        List<InjectedMethod> additions = className == null ? null : METHODS.get(className);
                        boolean borrowedScheduler = BORROWED_TASK_SCHEDULER.equals(className);
                        output.write(additions == null && !borrowedScheduler
                            ? bytes : transformClass(bytes, additions == null ? List.of() : additions,
                                borrowedScheduler));
                    }
                }
                output.closeEntry();
            }
            complete = true;
        } finally {
            if (complete) {
                Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static byte[] transformClass(
        byte[] bytecode, List<InjectedMethod> additions, boolean borrowedScheduler
    ) {
        Set<String> existing = new HashSet<>();
        ClassReader reader = new ClassReader(bytecode);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions
            ) {
                existing.add(name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces
            ) {
                String[] exposedInterfaces = interfaces;
                if (borrowedScheduler) {
                    exposedInterfaces = java.util.Arrays.stream(interfaces)
                        .filter(type -> !type.equals("java/lang/AutoCloseable"))
                        .toArray(String[]::new);
                }
                String exposedSignature = borrowedScheduler && signature != null
                    ? signature.replace("Ljava/lang/AutoCloseable;", "") : signature;
                super.visit(version, access, name, exposedSignature, superName, exposedInterfaces);
            }

            @Override
            public void visitEnd() {
                for (InjectedMethod method : additions) {
                    if (!existing.contains(method.name + method.descriptor)) addMethod(writer, method);
                }
                super.visitEnd();
            }
        }, 0);
        return writer.toByteArray();
    }

    private static void addMethod(ClassWriter writer, InjectedMethod method) {
        MethodVisitor visitor = writer.visitMethod(
            Opcodes.ACC_PUBLIC, method.name, method.descriptor, method.signature, null);
        visitor.visitCode();
        Type returnType = Type.getReturnType(method.descriptor);
        switch (returnType.getSort()) {
            case Type.VOID -> visitor.visitInsn(Opcodes.RETURN);
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> {
                visitor.visitInsn(Opcodes.ICONST_0);
                visitor.visitInsn(Opcodes.IRETURN);
            }
            case Type.LONG -> {
                visitor.visitInsn(Opcodes.LCONST_0);
                visitor.visitInsn(Opcodes.LRETURN);
            }
            case Type.FLOAT -> {
                visitor.visitInsn(Opcodes.FCONST_0);
                visitor.visitInsn(Opcodes.FRETURN);
            }
            case Type.DOUBLE -> {
                visitor.visitInsn(Opcodes.DCONST_0);
                visitor.visitInsn(Opcodes.DRETURN);
            }
            default -> {
                visitor.visitInsn(Opcodes.ACONST_NULL);
                visitor.visitInsn(Opcodes.ARETURN);
            }
        }
        visitor.visitMaxs(returnType.getSize(), Type.getArgumentsAndReturnSizes(method.descriptor) >> 2);
        visitor.visitEnd();
    }

    private static boolean signatureFile(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/")
            && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")
                || upper.endsWith(".EC"));
    }

    private static Map<String, List<InjectedMethod>> methods() {
        Map<String, List<InjectedMethod>> result = new HashMap<>();
        add(result, "net/minecraft/server/MinecraftServer",
            method("onlinePlayers", "()Ljava/util/Collection;",
                "()Ljava/util/Collection<Lnet/minecraft/server/level/ServerPlayer;>;"),
            method("findPlayer", "(Ljava/lang/String;)Ljava/util/Optional;",
                "(Ljava/lang/String;)Ljava/util/Optional<Lnet/minecraft/server/level/ServerPlayer;>;"),
            method("findPlayer", "(Ljava/util/UUID;)Ljava/util/Optional;",
                "(Ljava/util/UUID;)Ljava/util/Optional<Lnet/minecraft/server/level/ServerPlayer;>;"),
            method("loadedLevels", "()Ljava/util/Collection;",
                "()Ljava/util/Collection<Lnet/minecraft/server/level/ServerLevel;>;"),
            method("broadcast", "(Lnet/minecraft/network/chat/Component;)V"),
            method("broadcastPacket", "(Lnet/minecraft/network/protocol/Packet;)V",
                "(Lnet/minecraft/network/protocol/Packet<*>;)V"),
            method("restart", "()Z"));
        add(result, "net/minecraft/server/level/ServerPlayer",
            method("sendTitle", "(Lnet/minecraft/network/chat/Component;)V"),
            method("sendTitle", "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;III)V"),
            method("clearTitle", "()V"), method("clearTitle", "(Z)V"),
            method("kick", "(Lnet/minecraft/network/chat/Component;)V"),
            method("sendPacket", "(Lnet/minecraft/network/protocol/Packet;)V",
                "(Lnet/minecraft/network/protocol/Packet<*>;)V"),
            method("giveItem", "(Lnet/minecraft/world/item/ItemStack;)Z"),
            method("removeItems", "(Ljava/util/function/Predicate;I)I",
                "(Ljava/util/function/Predicate<Lnet/minecraft/world/item/ItemStack;>;I)I"),
            method("clearInventory", "()V"));
        add(result, "net/minecraft/server/level/ServerLevel",
            method("identifier", "()Ljava/lang/String;"),
            method("entities", "()Ljava/util/Collection;",
                "()Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;"),
            method("findEntity", "(Ljava/util/UUID;)Ljava/util/Optional;",
                "(Ljava/util/UUID;)Ljava/util/Optional<Lnet/minecraft/world/entity/Entity;>;"),
            method("findEntity", "(I)Ljava/util/Optional;",
                "(I)Ljava/util/Optional<Lnet/minecraft/world/entity/Entity;>;"),
            method("nearbyEntities", "(DDDD)Ljava/util/Collection;",
                "(DDDD)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;"),
            method("nearbyEntities", "(DDDDLjava/util/function/Predicate;)Ljava/util/Collection;",
                "(DDDDLjava/util/function/Predicate<Lnet/minecraft/world/entity/Entity;>;)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;"),
            method("clearWeather", "(I)V"), method("rain", "(I)V"), method("thunder", "(I)V"),
            method("block", "(III)Lnet/minecraft/world/level/block/state/BlockState;"),
            method("block", "(IIILnet/minecraft/world/level/block/state/BlockState;I)Z"),
            method("spawn", "(Lnet/minecraft/world/entity/Entity;)Z"),
            method("teleport", "(Lnet/minecraft/server/level/ServerPlayer;DDD)Z"),
            method("teleport", "(Lnet/minecraft/server/level/ServerPlayer;DDDFF)Z"));
        add(result, "net/minecraft/world/entity/Entity",
            method("nearbyEntities", "(D)Ljava/util/Collection;",
                "(D)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;"),
            method("nearbyEntities", "(DLjava/util/function/Predicate;)Ljava/util/Collection;",
                "(DLjava/util/function/Predicate<Lnet/minecraft/world/entity/Entity;>;)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;"),
            method("teleport", "(Lnet/minecraft/server/level/ServerLevel;DDD)Z"),
            method("teleport", "(Lnet/minecraft/server/level/ServerLevel;DDDFF)Z"));
        return Map.copyOf(result);
    }

    private static void add(Map<String, List<InjectedMethod>> methods, String owner, InjectedMethod... additions) {
        methods.put(owner, List.of(additions));
    }

    private static InjectedMethod method(String name, String descriptor) {
        return new InjectedMethod(name, descriptor, null);
    }

    private static InjectedMethod method(String name, String descriptor, String signature) {
        return new InjectedMethod(name, descriptor, signature);
    }

    private record InjectedMethod(String name, String descriptor, String signature) {
    }
}
