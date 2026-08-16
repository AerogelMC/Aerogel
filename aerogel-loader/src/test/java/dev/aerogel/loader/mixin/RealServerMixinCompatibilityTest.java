package dev.aerogel.loader.mixin;

import dev.aerogel.api.event.AerogelEvent;
import dev.aerogel.loader.install.ServerBundle;
import dev.aerogel.loader.runtime.ServerRuntime;
import dev.aerogel.loader.runtime.TransformingClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Offline compatibility check; it transforms classes but never starts Minecraft. */
@EnabledIfSystemProperty(named = "aerogel.test.serverJar", matches = ".+")
final class RealServerMixinCompatibilityTest {
    private static final List<String> TARGETS = List.of(
        "com.mojang.brigadier.tree.CommandNode",
        "net.minecraft.commands.Commands",
        "net.minecraft.network.Connection",
        "net.minecraft.network.PacketProcessor",
        "net.minecraft.network.PacketProcessor$ListenerAndPacket",
        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry",
        "net.minecraft.server.Main",
        "net.minecraft.server.MinecraftServer",
        "net.minecraft.server.dedicated.DedicatedServer",
        "net.minecraft.server.dedicated.DedicatedServer$1",
        "net.minecraft.server.level.ChunkMap",
        "net.minecraft.server.level.ChunkMap$TrackedEntity",
        "net.minecraft.server.level.GenerationChunkHolder",
        "net.minecraft.server.level.ServerEntity",
        "net.minecraft.server.level.ServerLevel",
        "net.minecraft.server.level.ServerPlayer",
        "net.minecraft.server.level.ServerPlayerGameMode",
        "net.minecraft.server.network.ServerCommonPacketListenerImpl",
        "net.minecraft.server.network.ServerGamePacketListenerImpl",
        "net.minecraft.server.network.ServerHandshakePacketListenerImpl",
        "net.minecraft.server.players.PlayerList",
        "net.minecraft.world.entity.Entity",
        "net.minecraft.world.entity.LivingEntity",
        "net.minecraft.world.entity.Mob",
        "net.minecraft.world.entity.TamableAnimal",
        "net.minecraft.world.entity.animal.Animal",
        "net.minecraft.world.entity.item.ItemEntity",
        "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal",
        "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal",
        "net.minecraft.world.entity.player.Player",
        "net.minecraft.world.entity.projectile.Projectile",
        "net.minecraft.world.item.BlockItem",
        "net.minecraft.world.level.Level",
        "net.minecraft.world.level.ServerExplosion",
        "net.minecraft.world.level.block.piston.PistonBaseBlock"
    );
    private static final List<String> DIRECT_LINKAGE_CLASSES = List.of(
        "dev/aerogel/loader/command/PluginsCommand.class",
        "dev/aerogel/loader/command/RestartCommand.class",
        "dev/aerogel/loader/command/InteractiveConsole.class",
        "dev/aerogel/loader/api/DirectCommandService.class",
        "dev/aerogel/loader/api/DirectBossBarService.class",
        "dev/aerogel/loader/api/DirectInventoryService.class",
        "dev/aerogel/loader/api/DirectDialogService.class",
        "dev/aerogel/loader/api/DirectScoreboardService.class",
        "dev/aerogel/loader/api/PluginTranslations.class",
        "dev/aerogel/loader/api/AerogelApiRuntime.class",
        "dev/aerogel/loader/event/EventHooks.class",
        "dev/aerogel/loader/internal/DeathDropCapture.class",
        "dev/aerogel/loader/internal/PlayerNameTagService.class",
        "dev/aerogel/loader/restart/RestartCoordinator.class"
    );

    @Test
    void coreMixinsTransformTheRealServerWithoutStartingIt() throws Exception {
        Path serverJar = Path.of(System.getProperty("aerogel.test.serverJar"));
        ServerBundle bundle = ServerBundle.extract(serverJar, Path.of("build", "mixin-test-bundle"));
        List<URL> urls = new ArrayList<>();
        urls.add(ServerRuntime.class.getProtectionDomain().getCodeSource().getLocation());
        urls.add(AerogelEvent.class.getProtectionDomain().getCodeSource().getLocation());
        for (Path artifact : bundle.classPath()) urls.add(artifact.toUri().toURL());

        try (TransformingClassLoader loader = new TransformingClassLoader(
            urls.toArray(URL[]::new), getClass().getClassLoader())) {
            Thread current = Thread.currentThread();
            ClassLoader previous = current.getContextClassLoader();
            current.setContextClassLoader(loader);
            try {
                MixinBootstrapper.initialize(loader, List.of());
                for (String target : TARGETS) Class.forName(target, false, loader);
                verifyDirectMinecraftLinkage(loader);
            } finally {
                current.setContextClassLoader(previous);
            }
        }
    }

    private static void verifyDirectMinecraftLinkage(ClassLoader minecraftLoader) throws Exception {
        List<String> failures = new ArrayList<>();
        ClassLoader compiledClasses = RealServerMixinCompatibilityTest.class.getClassLoader();
        for (String resource : DIRECT_LINKAGE_CLASSES) {
            try (InputStream input = compiledClasses.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing compiled class " + resource);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                        int access, String name, String descriptor, String signature, String[] exceptions
                    ) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(
                                int opcode, String owner, String methodName, String methodDescriptor,
                                boolean isInterface
                            ) {
                                if (!owner.startsWith("net/minecraft/")
                                    && !owner.startsWith("com/mojang/brigadier/")) return;
                                try {
                                    verifyMethod(minecraftLoader, owner, methodName, methodDescriptor);
                                } catch (Throwable failure) {
                                    failures.add(resource + " -> " + owner + "." + methodName
                                        + methodDescriptor + ": " + failure.getMessage());
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Direct Minecraft linkage mismatches:\n" + String.join("\n", failures));
        }
    }

    private static void verifyMethod(
        ClassLoader loader, String owner, String name, String descriptor
    ) throws Exception {
        Class<?> ownerType = Class.forName(owner.replace('/', '.'), false, loader);
        Class<?>[] parameters = Arrays.stream(Type.getArgumentTypes(descriptor))
            .map(type -> loadType(loader, type))
            .toArray(Class<?>[]::new);
        if ("<init>".equals(name)) {
            Constructor<?> constructor = ownerType.getDeclaredConstructor(parameters);
            if (constructor == null) throw new NoSuchMethodException(name + descriptor);
            return;
        }
        Class<?> returnType = loadType(loader, Type.getReturnType(descriptor));
        for (Method method : ownerType.getMethods()) {
            if (method.getName().equals(name)
                && Arrays.equals(method.getParameterTypes(), parameters)
                && method.getReturnType() == returnType) return;
        }
        throw new NoSuchMethodException("actual hierarchy has no exact descriptor");
    }

    private static Class<?> loadType(ClassLoader loader, Type type) {
        try {
            return switch (type.getSort()) {
                case Type.VOID -> void.class;
                case Type.BOOLEAN -> boolean.class;
                case Type.CHAR -> char.class;
                case Type.BYTE -> byte.class;
                case Type.SHORT -> short.class;
                case Type.INT -> int.class;
                case Type.FLOAT -> float.class;
                case Type.LONG -> long.class;
                case Type.DOUBLE -> double.class;
                case Type.ARRAY -> Class.forName(type.getDescriptor().replace('/', '.'), false, loader);
                case Type.OBJECT -> Class.forName(type.getClassName(), false, loader);
                default -> throw new IllegalArgumentException("Unsupported JVM type " + type);
            };
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
