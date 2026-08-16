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

    static String fingerprint() {
        return METHODS.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .flatMap(entry -> entry.getValue().stream()
                .map(method -> entry.getKey() + '#' + method.name + method.descriptor
                    + ':' + String.valueOf(method.signature) + ':'
                    + String.join(",", method.parameterNames)))
            .sorted()
            .collect(java.util.stream.Collectors.joining("\n"));
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
        for (String parameterName : method.parameterNames) {
            visitor.visitParameter(parameterName, 0);
        }
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
            namedGeneric("findPlayer", "(Ljava/lang/String;)Ljava/util/Optional;",
                "(Ljava/lang/String;)Ljava/util/Optional<Lnet/minecraft/server/level/ServerPlayer;>;", "name"),
            namedGeneric("findPlayer", "(Ljava/util/UUID;)Ljava/util/Optional;",
                "(Ljava/util/UUID;)Ljava/util/Optional<Lnet/minecraft/server/level/ServerPlayer;>;", "uniqueId"),
            method("loadedLevels", "()Ljava/util/Collection;",
                "()Ljava/util/Collection<Lnet/minecraft/server/level/ServerLevel;>;"),
            named("broadcast", "(Lnet/minecraft/network/chat/Component;)V", "message"),
            namedGeneric("broadcastPacket", "(Lnet/minecraft/network/protocol/Packet;)V",
                "(Lnet/minecraft/network/protocol/Packet<*>;)V", "packet"),
            method("restart", "()Z"));
        add(result, "net/minecraft/server/level/ServerPlayer",
            named("setDisplayName", "(Lnet/minecraft/network/chat/Component;)V", "displayName"),
            method("clearDisplayName", "()V"),
            named("setTabListName", "(Lnet/minecraft/network/chat/Component;)V", "name"),
            method("clearTabListName", "()V"),
            named("setTabListHidden", "(Z)V", "hidden"),
            method("isTabListHidden", "()Z"),
            named("setNameTagHidden", "(Z)V", "hidden"),
            method("isNameTagHidden", "()Z"),
            named("setTabListHeader", "(Lnet/minecraft/network/chat/Component;)V", "header"),
            named("setTabListFooter", "(Lnet/minecraft/network/chat/Component;)V", "footer"),
            named("setTabListHeaderFooter", "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V", "header", "footer"),
            method("clearTabListHeaderFooter", "()V"),
            named("sendTitle", "(Lnet/minecraft/network/chat/Component;)V", "title"),
            named("sendTitle", "(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;III)V", "title", "subtitle", "fadeInTicks", "stayTicks", "fadeOutTicks"),
            method("clearTitle", "()V"), named("clearTitle", "(Z)V", "resetTimes"),
            named("kick", "(Lnet/minecraft/network/chat/Component;)V", "reason"),
            namedGeneric("sendPacket", "(Lnet/minecraft/network/protocol/Packet;)V",
                "(Lnet/minecraft/network/protocol/Packet<*>;)V", "packet"),
            named("giveItem", "(Lnet/minecraft/world/item/ItemStack;)Z", "stack"),
            namedGeneric("removeItems", "(Ljava/util/function/Predicate;I)I",
                "(Ljava/util/function/Predicate<Lnet/minecraft/world/item/ItemStack;>;I)I", "filter", "maximum"),
            method("clearInventory", "()V"),
            method("respawn", "()Lnet/minecraft/server/level/ServerPlayer;"),
            named("respawn", "(Z)Lnet/minecraft/server/level/ServerPlayer;", "keepEverything"),
            named("setBlock", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", "position", "state"),
            namedGeneric("setBlocks", "(Ljava/util/Map;)V",
                "(Ljava/util/Map<Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;>;)V", "blocks"),
            named("resetBlock", "(Lnet/minecraft/core/BlockPos;)V", "position"),
            namedGeneric("resetBlocks", "(Ljava/util/Collection;)V",
                "(Ljava/util/Collection<Lnet/minecraft/core/BlockPos;>;)V", "positions"),
            named("setBlockEntity", "(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", "blockEntity"),
            named("setBlockBreakProgress", "(Lnet/minecraft/core/BlockPos;I)V", "position", "progress"),
            named("clearBlockBreakProgress", "(Lnet/minecraft/core/BlockPos;)V", "position"),
            named("playBlockEvent", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;II)V", "position", "block", "type", "data"),
            named("setVisible", "(Lnet/minecraft/world/entity/Entity;Z)V", "entity", "visible"),
            named("isVisible", "(Lnet/minecraft/world/entity/Entity;)Z", "entity"),
            named("setGlowing", "(Lnet/minecraft/world/entity/Entity;Z)V", "entity", "glowing"),
            named("resetGlowing", "(Lnet/minecraft/world/entity/Entity;)V", "entity"),
            named("setGlowColorOverride", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/scores/TeamColor;)V", "entity", "color"),
            named("resetGlowColorOverride", "(Lnet/minecraft/world/entity/Entity;)V", "entity"),
            named("setInvisible", "(Lnet/minecraft/world/entity/Entity;Z)V", "entity", "invisible"),
            named("resetInvisible", "(Lnet/minecraft/world/entity/Entity;)V", "entity"),
            named("setOnFire", "(Lnet/minecraft/world/entity/Entity;Z)V", "entity", "onFire"),
            named("resetOnFire", "(Lnet/minecraft/world/entity/Entity;)V", "entity"),
            named("setEquipment", "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V", "entity", "slot", "item"),
            named("resetEquipment", "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V", "entity", "slot"),
            named("setEntityVelocity", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V", "entity", "velocity"),
            named("setEntityPosition", "(Lnet/minecraft/world/entity/Entity;DDDFF)V", "entity", "x", "y", "z", "yaw", "pitch"),
            named("setEntityHeadRotation", "(Lnet/minecraft/world/entity/Entity;F)V", "entity", "yaw"),
            named("playHandSwing", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)V", "entity", "hand"),
            named("playCriticalHit", "(Lnet/minecraft/world/entity/Entity;Z)V", "entity", "magic"),
            named("playWakeUp", "(Lnet/minecraft/world/entity/Entity;)V", "entity"),
            named("playEntityEvent", "(Lnet/minecraft/world/entity/Entity;B)V", "entity", "eventId"),
            named("setEntityLeash", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V", "entity", "holder"),
            named("setCameraView", "(Lnet/minecraft/world/entity/Entity;)V", "entity"),
            method("resetCameraView", "()V"),
            named("spawnParticle", "(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)V", "particle", "x", "y", "z", "count", "offsetX", "offsetY", "offsetZ", "speed"),
            namedGeneric("playSound", "(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;DDDFF)V",
                "(Lnet/minecraft/core/Holder<Lnet/minecraft/sounds/SoundEvent;>;Lnet/minecraft/sounds/SoundSource;DDDFF)V", "sound", "source", "x", "y", "z", "volume", "pitch"),
            named("stopSound", "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V", "sound", "source"),
            method("stopSounds", "()V"),
            named("setExperienceBar", "(FII)V", "progress", "level", "totalExperience"),
            method("resetExperienceBar", "()V"),
            named("setHealthBar", "(FIF)V", "health", "food", "saturation"),
            method("resetHealthBar", "()V"),
            named("setWeather", "(FF)V", "rainLevel", "thunderLevel"),
            method("resetWeather", "()V"),
            named("setWorldBorder", "(Lnet/minecraft/world/level/border/WorldBorder;)V", "border"),
            method("resetWorldBorder", "()V"),
            method("clearViewOverrides", "()V"));
        add(result, "net/minecraft/server/level/ServerLevel",
            method("identifier", "()Ljava/lang/String;"),
            method("entities", "()Ljava/util/Collection;",
                "()Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;"),
            namedGeneric("findEntity", "(Ljava/util/UUID;)Ljava/util/Optional;",
                "(Ljava/util/UUID;)Ljava/util/Optional<Lnet/minecraft/world/entity/Entity;>;", "uniqueId"),
            namedGeneric("findEntity", "(I)Ljava/util/Optional;",
                "(I)Ljava/util/Optional<Lnet/minecraft/world/entity/Entity;>;", "entityId"),
            namedGeneric("nearbyEntities", "(DDDD)Ljava/util/Collection;",
                "(DDDD)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;", "x", "y", "z", "radius"),
            namedGeneric("nearbyEntities", "(DDDDLjava/util/function/Predicate;)Ljava/util/Collection;",
                "(DDDDLjava/util/function/Predicate<Lnet/minecraft/world/entity/Entity;>;)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;", "x", "y", "z", "radius", "filter"),
            named("clearWeather", "(I)V", "durationTicks"),
            named("rain", "(I)V", "durationTicks"),
            named("thunder", "(I)V", "durationTicks"),
            named("block", "(III)Lnet/minecraft/world/level/block/state/BlockState;", "x", "y", "z"),
            named("block", "(IIILnet/minecraft/world/level/block/state/BlockState;I)Z", "x", "y", "z", "state", "flags"),
            named("spawn", "(Lnet/minecraft/world/entity/Entity;)Z", "entity"),
            named("teleport", "(Lnet/minecraft/server/level/ServerPlayer;DDD)Z", "player", "x", "y", "z"),
            named("teleport", "(Lnet/minecraft/server/level/ServerPlayer;DDDFF)Z", "player", "x", "y", "z", "yaw", "pitch"));
        add(result, "net/minecraft/world/entity/Entity",
            namedGeneric("nearbyEntities", "(D)Ljava/util/Collection;",
                "(D)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;", "radius"),
            namedGeneric("nearbyEntities", "(DLjava/util/function/Predicate;)Ljava/util/Collection;",
                "(DLjava/util/function/Predicate<Lnet/minecraft/world/entity/Entity;>;)Ljava/util/Collection<Lnet/minecraft/world/entity/Entity;>;", "radius", "filter"),
            named("teleport", "(Lnet/minecraft/server/level/ServerLevel;DDD)Z", "destination", "x", "y", "z"),
            named("teleport", "(Lnet/minecraft/server/level/ServerLevel;DDDFF)Z", "destination", "x", "y", "z", "yaw", "pitch"));
        return Map.copyOf(result);
    }

    private static void add(Map<String, List<InjectedMethod>> methods, String owner, InjectedMethod... additions) {
        methods.put(owner, List.of(additions));
    }

    private static InjectedMethod method(String name, String descriptor) {
        return new InjectedMethod(name, descriptor, null, List.of());
    }

    private static InjectedMethod method(String name, String descriptor, String signature) {
        return new InjectedMethod(name, descriptor, signature, List.of());
    }

    private static InjectedMethod named(String name, String descriptor, String... parameterNames) {
        return namedGeneric(name, descriptor, null, parameterNames);
    }

    private static InjectedMethod namedGeneric(
        String name, String descriptor, String signature, String... parameterNames
    ) {
        int parameterCount = Type.getArgumentTypes(descriptor).length;
        if (parameterNames.length != parameterCount) {
            throw new IllegalArgumentException(name + descriptor + " has " + parameterCount
                + " parameters, not " + parameterNames.length);
        }
        return new InjectedMethod(name, descriptor, signature, List.of(parameterNames));
    }

    private record InjectedMethod(
        String name, String descriptor, String signature, List<String> parameterNames
    ) {
    }
}
