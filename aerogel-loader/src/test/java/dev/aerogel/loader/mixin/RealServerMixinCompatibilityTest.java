package dev.aerogel.loader.mixin;

import dev.aerogel.api.event.AerogelEvent;
import dev.aerogel.loader.install.ServerBundle;
import dev.aerogel.loader.runtime.ServerRuntime;
import dev.aerogel.loader.runtime.TransformingClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
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
final class RealServerMixinCompatibilityTest {

    private static final List<String> TARGETS = List.of(
        "com.mojang.brigadier.tree.CommandNode",
        "net.minecraft.commands.Commands",
        "net.minecraft.network.Connection",
        "net.minecraft.network.PacketEncoder",
        "net.minecraft.network.PacketProcessor",
        "net.minecraft.network.PacketProcessor$ListenerAndPacket",
        "net.minecraft.network.protocol.PacketUtils",
        "net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket",
        "net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket",
        "net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket",
        "net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket",
        "net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket",
        "net.minecraft.network.protocol.game.ServerboundSignUpdatePacket",
        "net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket",
        "net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket",
        "net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket",
        "net.minecraft.network.protocol.game.ServerboundUseItemOnPacket",
        "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket",
        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry",
        "net.minecraft.server.Main",
        "net.minecraft.server.MinecraftServer",
        "net.minecraft.server.ReloadableServerRegistries$Holder",
        "net.minecraft.server.dedicated.DedicatedServer",
        "net.minecraft.server.dedicated.DedicatedServer$1",
        "net.minecraft.server.level.ChunkMap",
        "net.minecraft.server.level.ChunkTracker",
        "net.minecraft.server.level.ChunkMap$TrackedEntity",
        "net.minecraft.server.level.GenerationChunkHolder",
        "net.minecraft.server.level.ServerChunkCache",
        "net.minecraft.server.level.ServerEntity",
        "net.minecraft.server.level.ServerEntityGetter",

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
        "net.minecraft.world.entity.ai.village.poi.PoiManager",
        "net.minecraft.world.entity.ai.village.poi.PoiRecord",
        "net.minecraft.world.entity.ai.village.poi.PoiSection",
        "net.minecraft.world.entity.TamableAnimal",
        "net.minecraft.world.entity.animal.Animal",
        "net.minecraft.world.entity.item.ItemEntity",
        "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal",
        "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal",
        "net.minecraft.world.entity.player.Player",
        "net.minecraft.world.entity.projectile.Projectile",
        "net.minecraft.world.level.entity.EntityLookup",
        "net.minecraft.world.level.entity.EntitySectionStorage",
        "net.minecraft.world.level.chunk.storage.EntityStorage",
        "net.minecraft.world.level.entity.PersistentEntitySectionManager",
        "net.minecraft.server.level.ServerLevel$EntityCallbacks",
        "net.minecraft.world.item.BlockItem",
        "net.minecraft.world.item.ItemStack",
        "net.minecraft.world.item.crafting.RecipeHolder",
        "net.minecraft.world.item.crafting.RecipeManager",
        "net.minecraft.world.level.Level",
        "net.minecraft.world.level.PotentialCalculator",
        "net.minecraft.world.level.LocalMobCapCalculator",
        "net.minecraft.world.level.LocalMobCapCalculator$MobCounts",
        "net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint",
        "net.minecraft.world.level.NaturalSpawner",
        "net.minecraft.world.level.NaturalSpawner$SpawnState",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$SimpleNeighborUpdate",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$FullNeighborUpdate",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$MultiNeighborUpdate",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$ShapeUpdate",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater",
        "net.minecraft.world.ticks.LevelTicks",
        "net.minecraft.world.level.chunk.storage.SectionStorage",
        "net.minecraft.world.level.ServerExplosion",
        "net.minecraft.world.level.TicketStorage",
        "net.minecraft.world.level.block.entity.BlockEntity",
        "net.minecraft.world.level.storage.loot.LootTable",
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
        "dev/aerogel/loader/internal/ViewerScoreboard.class",
        "dev/aerogel/loader/internal/PlayerScoreboardView.class",
        "dev/aerogel/loader/api/PluginTranslations.class",
        "dev/aerogel/loader/api/AerogelApiRuntime.class",
        "dev/aerogel/loader/api/DirectPersistentDataService.class",
        "dev/aerogel/loader/internal/AerogelPersistentSavedData.class",
        "dev/aerogel/loader/internal/PersistentDataViews.class",
        "dev/aerogel/loader/internal/PersistentDataViews$EntityView.class",
        "dev/aerogel/loader/internal/PersistentDataViews$EntityContainer.class",
        "dev/aerogel/loader/internal/PersistentDataViews$ItemView.class",
        "dev/aerogel/loader/internal/PersistentDataViews$SavedView.class",
        "dev/aerogel/loader/internal/PersistentDataViews$ItemContainer.class",
        "dev/aerogel/loader/internal/PersistentDataViews$SavedContainer.class",
        "dev/aerogel/loader/internal/PersistentDataViews$TypedContainer.class",
        "dev/aerogel/loader/internal/ItemBuilders.class",
        "dev/aerogel/loader/internal/ItemBuilders$Builder.class",
        "dev/aerogel/loader/api/DirectRecipeService.class",
        "dev/aerogel/loader/api/DirectLootService.class",
        "dev/aerogel/loader/api/DirectMenuService.class",
        "dev/aerogel/loader/api/DirectVirtualEntityService.class",
        "dev/aerogel/loader/api/DirectVirtualEntityService$Virtual.class",
        "dev/aerogel/loader/api/DirectBlockBatchService$Batch.class",
        "dev/aerogel/loader/event/EventHooks.class",
        "dev/aerogel/loader/internal/DeathDropCapture.class",
        "dev/aerogel/loader/internal/PlayerNameTagService.class",
        "dev/aerogel/loader/internal/PlayerViewService.class",
        "dev/aerogel/loader/restart/RestartCoordinator.class"
    );

    @Test
    void coreMixinsTransformTheRealServerWithoutStartingIt() throws Exception {
        Path serverJar = Path.of(System.getProperty(
            "aerogel.test.serverJar",
            "C:/Users/kcomw/Documents/Codex/2026-08-20/"
                + "c-users-kcomw-desktop-aerogel2/work/server-benchmark/runtime/26.2/server.jar"));
        if (!java.nio.file.Files.exists(serverJar)) return;
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
                for (String target : TARGETS) {
                    Class.forName(target, false, loader);
                }
                verifyBlockTargetPacketAdapters(loader);
                System.out.println("=== ServerLevel.tickChunk bytecode ===");
                try (InputStream in = loader.getResourceAsStream("net/minecraft/server/level/ServerLevel.class")) {
                    new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if ("tickChunk".equals(name)) {
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                        System.out.println("  INVOKE: " + owner + "." + name + descriptor);
                                    }
                                    @Override
                                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                        System.out.println("  FIELD: " + owner + "." + name + " " + descriptor);
                                    }
                                };
                            }
                            return null;
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                Class<?> slClass = Class.forName("net.minecraft.server.level.ServerLevel", false, loader);
                System.out.println("=== ServerLevel Fields ===");
                for (java.lang.reflect.Field f : slClass.getDeclaredFields()) {
                    if (java.util.Collection.class.isAssignableFrom(f.getType()) || java.util.Map.class.isAssignableFrom(f.getType()) || f.getType().getName().contains("fastutil")) {
                        System.out.println("  COLLECTION FIELD: " + f.getName() + " " + f.getType());
                    }
                }





































                Class.forName(
                    "dev.aerogel.loader.internal.AerogelPersistentSavedData", true, loader);
                verifyTicketActivityIndex(loader);
                verifyDirectMinecraftLinkage(loader);
                verifyViewerScoreboard(loader);

            } finally {
                current.setContextClassLoader(previous);
            }
        }
    }

    private static void verifyViewerScoreboard(ClassLoader loader) throws Exception {
        Class<?> boardType = loader.loadClass("dev.aerogel.loader.internal.ViewerScoreboard");
        Class<?> baseType = loader.loadClass("net.minecraft.world.scores.Scoreboard");
        Class<?> objectiveType = loader.loadClass("net.minecraft.world.scores.Objective");
        Class<?> criteriaType = loader.loadClass("net.minecraft.world.scores.criteria.ObjectiveCriteria");
        Class<?> renderType = loader.loadClass("net.minecraft.world.scores.criteria.ObjectiveCriteria$RenderType");
        Class<?> componentType = loader.loadClass("net.minecraft.network.chat.Component");
        Class<?> formatType = loader.loadClass("net.minecraft.network.chat.numbers.NumberFormat");
        Class<?> holderType = loader.loadClass("net.minecraft.world.scores.ScoreHolder");
        Class<?> slotType = loader.loadClass("net.minecraft.world.scores.DisplaySlot");
        Class<?> accessType = loader.loadClass("net.minecraft.world.scores.ScoreAccess");
        List<Object> firstPackets = new ArrayList<>();
        List<Object> secondPackets = new ArrayList<>();
        var constructor = boardType.getConstructor(java.util.function.Consumer.class);
        Object first = constructor.newInstance((java.util.function.Consumer<Object>) firstPackets::add);
        Object second = constructor.newInstance((java.util.function.Consumer<Object>) secondPackets::add);
        Method add = baseType.getMethod("addObjective", String.class, criteriaType, componentType,
            renderType, boolean.class, formatType);
        Object title = componentType.getMethod("literal", String.class).invoke(null, "Private");
        Object criteria = criteriaType.getField("DUMMY").get(null);
        Object integer = renderType.getField("INTEGER").get(null);
        Object firstObjective = add.invoke(first, "same_name", criteria, title, integer, false, null);
        Object secondObjective = add.invoke(second, "same_name", criteria, title, integer, false, null);
        Object holder = holderType.getMethod("forNameOnly", String.class).invoke(null, "Balance");
        Method score = baseType.getMethod("getOrCreatePlayerScore", holderType, objectiveType);
        Object firstScore = score.invoke(first, holder, firstObjective);
        Object secondScore = score.invoke(second, holder, secondObjective);
        accessType.getMethod("set", int.class).invoke(firstScore, 10);
        accessType.getMethod("set", int.class).invoke(secondScore, 99);
        org.junit.jupiter.api.Assertions.assertEquals(10, accessType.getMethod("get").invoke(firstScore));
        org.junit.jupiter.api.Assertions.assertEquals(99, accessType.getMethod("get").invoke(secondScore));
        int secondCount = secondPackets.size();
        accessType.getMethod("set", int.class).invoke(firstScore, 20);
        org.junit.jupiter.api.Assertions.assertEquals(secondCount, secondPackets.size());
        Object latest = firstPackets.getLast();
        org.junit.jupiter.api.Assertions.assertEquals("ClientboundSetScorePacket", latest.getClass().getSimpleName());
        org.junit.jupiter.api.Assertions.assertEquals(20, latest.getClass().getMethod("score").invoke(latest));
        Object sidebar = slotType.getField("SIDEBAR").get(null);
        baseType.getMethod("setDisplayObjective", slotType, objectiveType).invoke(first, sidebar, firstObjective);
        Object team = baseType.getMethod("addPlayerTeam", String.class).invoke(first, "private_team");
        Class<?> teamType = loader.loadClass("net.minecraft.world.scores.PlayerTeam");
        baseType.getMethod("addPlayerToTeam", String.class, teamType).invoke(first, "Balance", team);
        List<Object> snapshot = new ArrayList<>();
        boardType.getMethod("snapshot", baseType, java.util.function.Consumer.class)
            .invoke(null, first, (java.util.function.Consumer<Object>) snapshot::add);
        org.junit.jupiter.api.Assertions.assertEquals("ClientboundSetObjectivePacket", snapshot.getFirst().getClass().getSimpleName());
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.stream().anyMatch(p -> p.getClass().getSimpleName().equals("ClientboundSetPlayerTeamPacket")));
        List<Object> cleared = new ArrayList<>();
        boardType.getMethod("clearDisplay", baseType, java.util.function.Consumer.class)
            .invoke(null, first, (java.util.function.Consumer<Object>) cleared::add);
        org.junit.jupiter.api.Assertions.assertTrue(cleared.stream().anyMatch(p -> p.getClass().getSimpleName().equals("ClientboundSetObjectivePacket")));
        baseType.getMethod("resetSinglePlayerScore", holderType, objectiveType).invoke(first, holder, firstObjective);
        org.junit.jupiter.api.Assertions.assertEquals("ClientboundResetScorePacket", firstPackets.getLast().getClass().getSimpleName());
        org.junit.jupiter.api.Assertions.assertEquals(99, accessType.getMethod("get").invoke(secondScore));
    }

    private static void verifyTicketActivityIndex(ClassLoader loader) throws Exception {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        try {
            verifyTicketActivityIndexAfterBootstrap(loader);
        } finally {
            // Minecraft's bootstrap replaces the process-wide streams. This test
            // loads it through an isolated server class loader, so leaving those
            // wrappers installed would leak that loader into unrelated tests.
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static void verifyTicketActivityIndexAfterBootstrap(
        ClassLoader loader
    ) throws Exception {
        Class.forName("net.minecraft.SharedConstants", true, loader)
            .getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap", true, loader)
            .getMethod("bootStrap").invoke(null);
        Class<?> storageType = Class.forName(
            "net.minecraft.world.level.TicketStorage", true, loader);
        Class<?> ticketType = Class.forName(
            "net.minecraft.server.level.Ticket", true, loader);
        Class<?> typeType = Class.forName(
            "net.minecraft.server.level.TicketType", true, loader);
        Object storage = storageType.getConstructor().newInstance();
        Method shouldKeepActive = storageType.getMethod("shouldKeepDimensionActive");
        if ((boolean) shouldKeepActive.invoke(storage)) {
            throw new AssertionError("Empty TicketStorage is dimension-active");
        }

        Method typeKeepsActive = typeType.getMethod("shouldKeepDimensionActive");
        Object activeType = null;
        for (java.lang.reflect.Field field : typeType.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && field.getType() == typeType) {
                Object candidate = field.get(null);
                if ((boolean) typeKeepsActive.invoke(candidate)) {
                    activeType = candidate;
                    break;
                }
            }
        }
        if (activeType == null) {
            throw new AssertionError("No dimension-active vanilla ticket type found");
        }

        Constructor<?> ticketConstructor = ticketType.getConstructor(typeType, int.class);
        Method add = storageType.getMethod("addTicket", long.class, ticketType);
        Method remove = storageType.getMethod("removeTicket", long.class, ticketType);
        Object first = ticketConstructor.newInstance(activeType, 31);
        if (!(boolean) add.invoke(storage, 42L, first)
            || !(boolean) shouldKeepActive.invoke(storage)) {
            throw new AssertionError("Added active ticket was not indexed");
        }
        Object equivalent = ticketConstructor.newInstance(activeType, 31);
        if ((boolean) add.invoke(storage, 42L, equivalent)) {
            throw new AssertionError("Equivalent ticket was unexpectedly duplicated");
        }
        if (!(boolean) remove.invoke(storage, 42L, equivalent)
            || (boolean) shouldKeepActive.invoke(storage)) {
            throw new AssertionError("Removed active ticket remained indexed");
        }

        Object predicateTicket = ticketConstructor.newInstance(activeType, 32);
        if (!(boolean) add.invoke(storage, 43L, predicateTicket)) {
            throw new AssertionError("Predicate-removal ticket was not added");
        }
        Class<?> predicateType = Class.forName(
            "net.minecraft.world.level.TicketStorage$TicketPredicate", true, loader);
        Object predicate = java.lang.reflect.Proxy.newProxyInstance(
            loader, new Class<?>[] { predicateType },
            (proxy, method, arguments) -> "test".equals(method.getName()));
        Class<?> mapType = Class.forName(
            "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap", true, loader);
        storageType.getMethod("removeTicketIf", predicateType, mapType)
            .invoke(storage, predicate, null);
        if ((boolean) shouldKeepActive.invoke(storage)) {
            throw new AssertionError("Predicate-removed active ticket remained indexed");
        }

        verifyExactSimulationDistances(
            loader, storageType, ticketType, typeType, ticketConstructor);
    }

    private static void verifyExactSimulationDistances(
        ClassLoader loader,
        Class<?> storageType,
        Class<?> ticketType,
        Class<?> typeType,
        Constructor<?> ticketConstructor
    ) throws Exception {
        Object storage = storageType.getConstructor().newInstance();
        Method doesSimulate = typeType.getMethod("doesSimulate");
        Object simulationType = null;
        for (java.lang.reflect.Field field : typeType.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && field.getType() == typeType) {
                Object candidate = field.get(null);
                if ((boolean) doesSimulate.invoke(candidate)) {
                    simulationType = candidate;
                    break;
                }
            }
        }
        if (simulationType == null) {
            throw new AssertionError("No vanilla simulation ticket type found");
        }

        Class<?> trackerType = Class.forName(
            "net.minecraft.server.level.SimulationChunkTracker", true, loader);
        Class<?> chunkPosType = Class.forName(
            "net.minecraft.world.level.ChunkPos", true, loader);
        Object tracker = trackerType.getConstructor(storageType).newInstance(storage);
        Method add = storageType.getMethod("addTicket", long.class, ticketType);
        Method remove = storageType.getMethod("removeTicket", long.class, ticketType);
        Method runAllUpdates = trackerType.getMethod("runAllUpdates");
        Method getLevel = trackerType.getMethod("getLevel", chunkPosType);
        Method pack = chunkPosType.getMethod("pack", int.class, int.class);
        Constructor<?> chunkPos = chunkPosType.getConstructor(int.class, int.class);

        int firstX = -7;
        int firstZ = 11;
        int firstLevel = 29;
        long firstKey = (long) pack.invoke(null, firstX, firstZ);
        Object first = ticketConstructor.newInstance(simulationType, firstLevel);
        if (!(boolean) add.invoke(storage, firstKey, first)) {
            throw new AssertionError("Simulation ticket was not added");
        }
        awaitExactLevel(runAllUpdates, getLevel, tracker, chunkPos,
            firstX, firstZ, firstLevel);
        assertChebyshevField(
            getLevel, tracker, chunkPos, firstX, firstZ, firstLevel);

        int secondX = -4;
        int secondZ = 9;
        int secondLevel = 31;
        long secondKey = (long) pack.invoke(null, secondX, secondZ);
        Object second = ticketConstructor.newInstance(simulationType, secondLevel);
        if (!(boolean) add.invoke(storage, secondKey, second)) {
            throw new AssertionError("Overlapping simulation ticket was not added");
        }
        awaitExactLevel(runAllUpdates, getLevel, tracker, chunkPos,
            secondX, secondZ, secondLevel);
        for (int x = -12; x <= 1; x++) {
            for (int z = 4; z <= 16; z++) {
                int firstDistance = Math.max(Math.abs(x - firstX), Math.abs(z - firstZ));
                int secondDistance = Math.max(Math.abs(x - secondX), Math.abs(z - secondZ));
                int expected = Math.min(33, Math.min(
                    firstLevel + firstDistance, secondLevel + secondDistance));
                assertChunkLevel(getLevel, tracker, chunkPos, x, z, expected);
            }
        }

        Object equivalentFirst = ticketConstructor.newInstance(
            simulationType, firstLevel);
        if (!(boolean) remove.invoke(storage, firstKey, equivalentFirst)) {
            throw new AssertionError("Simulation ticket was not removed");
        }
        awaitExactLevel(runAllUpdates, getLevel, tracker, chunkPos,
            firstX, firstZ, Math.min(33, secondLevel
                + Math.max(Math.abs(firstX - secondX), Math.abs(firstZ - secondZ))));
        assertChebyshevField(
            getLevel, tracker, chunkPos, secondX, secondZ, secondLevel);

        Object equivalentSecond = ticketConstructor.newInstance(
            simulationType, secondLevel);
        if (!(boolean) remove.invoke(storage, secondKey, equivalentSecond)) {
            throw new AssertionError("Second simulation ticket was not removed");
        }
        awaitExactLevel(runAllUpdates, getLevel, tracker, chunkPos,
            secondX, secondZ, 33);
        for (int x = -12; x <= 1; x++) {
            for (int z = 4; z <= 16; z++) {
                assertChunkLevel(getLevel, tracker, chunkPos, x, z, 33);
            }
        }
    }

    private static void awaitExactLevel(
        Method runAllUpdates,
        Method getLevel,
        Object tracker,
        Constructor<?> chunkPos,
        int x,
        int z,
        int expected
    ) throws Exception {
        Object position = chunkPos.newInstance(x, z);
        Method pumpMainThread = tracker.getClass().getClassLoader()
            .loadClass("dev.aerogel.loader.context.NativeTickCoordinator")
            .getMethod("pumpMainThread");
        long deadline = System.nanoTime() + 5_000_000_000L;
        do {
            runAllUpdates.invoke(tracker);
            pumpMainThread.invoke(null);
            if ((int) getLevel.invoke(tracker, position) == expected) return;
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        assertChunkLevel(getLevel, tracker, chunkPos, x, z, expected);
    }

    private static void assertChebyshevField(
        Method getLevel,
        Object tracker,
        Constructor<?> chunkPos,
        int sourceX,
        int sourceZ,
        int sourceLevel
    ) throws Exception {
        for (int x = sourceX - 6; x <= sourceX + 6; x++) {
            for (int z = sourceZ - 6; z <= sourceZ + 6; z++) {
                int distance = Math.max(Math.abs(x - sourceX), Math.abs(z - sourceZ));
                assertChunkLevel(getLevel, tracker, chunkPos, x, z,
                    Math.min(33, sourceLevel + distance));
            }
        }
    }

    private static void assertChunkLevel(
        Method getLevel,
        Object tracker,
        Constructor<?> chunkPos,
        int x,
        int z,
        int expected
    ) throws Exception {
        int actual = (int) getLevel.invoke(tracker, chunkPos.newInstance(x, z));
        if (actual != expected) {
            throw new AssertionError(
                "Unexpected simulation level at [" + x + ", " + z
                    + "]: expected=" + expected + ", actual=" + actual);
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

    private static void verifyBlockTargetPacketAdapters(ClassLoader loader)
        throws Exception {
        for (String target : TARGETS) {
            if (!target.startsWith("net.minecraft.network.protocol.game.Serverbound")) {
                continue;
            }
            Class<?> type = Class.forName(target, false, loader);
            boolean publishesTarget = Arrays.stream(type.getInterfaces())
                .anyMatch(contract -> contract.getName().equals(
                    "dev.aerogel.loader.network.BlockTargetPacket"));
            if (!publishesTarget) {
                throw new AssertionError(
                    "Block-target packet did not publish its Context target: " + target
                        + ", loader=" + type.getClassLoader()
                        + ", interfaces=" + Arrays.toString(type.getInterfaces()));
            }
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
