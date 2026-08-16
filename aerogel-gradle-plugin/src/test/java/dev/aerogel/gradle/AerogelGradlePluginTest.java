package dev.aerogel.gradle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aerogel.loader.mixin.MixinBootstrapper;
import dev.aerogel.loader.plugin.PluginDescriptor;
import dev.aerogel.loader.runtime.TransformingClassLoader;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerogelGradlePluginTest {
    @TempDir
    Path project;

    @Test
    void buildsAnExternalPluginWithVanillaTypesAndGeneratedMetadata() throws Exception {
        prepareProject();

        BuildResult result = runner("clean", "build", "--stacktrace").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":setupAerogelDevelopment").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAerogelPluginMetadata").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateAerogelPluginJar").getOutcome());
        Path pluginJar = project.resolve("build/libs/sample-1.2.3.jar");
        assertTrue(Files.isRegularFile(pluginJar));
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            assertNotNull(jar.getJarEntry("sample/ExamplePlugin.class"));
            assertNotNull(jar.getJarEntry("dev/aerogel/generated/sample_plugin/mixin/ServerTick.class"));
            assertNotNull(jar.getJarEntry("aerogel.plugin.json"));
            assertNotNull(jar.getJarEntry("sample_plugin.generated.mixins.json"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("net/minecraft/")));
            JsonObject metadata = JsonParser.parseReader(new InputStreamReader(
                jar.getInputStream(jar.getJarEntry("aerogel.plugin.json")), StandardCharsets.UTF_8))
                .getAsJsonObject();
            assertEquals("sample_plugin", metadata.get("id").getAsString());
            assertEquals("Sample Plugin", metadata.get("name").getAsString());
            assertEquals("1.2.3", metadata.get("version").getAsString());
            assertEquals(">=26.2", metadata.get("minecraft").getAsString());
            assertEquals("sample.ExamplePlugin",
                metadata.getAsJsonArray("entrypoints").get(0).getAsString());
            assertTrue(metadata.getAsJsonArray("mixins").asList().stream()
                .anyMatch(element -> element.getAsString().equals("sample_plugin.generated.mixins.json")));
        }
        assertTrue(Files.isRegularFile(project.resolve(
            "build/aerogel/minecraft/26.2/classpath/versions/26.2/server.jar")));
        String idea = Files.readString(project.resolve(".idea/misc.xml"));
        assertTrue(idea.contains("dev.aerogel.api.event.EventHandler"));
        assertTrue(idea.contains("TYPE=\"class\" FQNAME=\"sample.ExamplePlugin\"")
            || idea.contains("FQNAME=\"sample.ExamplePlugin\" TYPE=\"class\""));
        Path developmentServer = project.resolve(
            "build/aerogel/minecraft/26.2/classpath/versions/26.2/server.jar");
        try (JarFile jar = new JarFile(developmentServer.toFile())) {
            byte[] scheduler = jar.getInputStream(jar.getJarEntry(
                "net/minecraft/util/thread/TaskScheduler.class")).readAllBytes();
            assertFalse(java.util.List.of(new ClassReader(scheduler).getInterfaces())
                .contains("java/lang/AutoCloseable"));
        }
        assertGeneratedMixinApplies(pluginJar, developmentServer);
    }

    @Test
    void rejectsMinecraftClassesBundledByThePluginProject() throws Exception {
        prepareProject();
        Path source = project.resolve("src/main/java/net/minecraft/AccidentalCopy.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package net.minecraft; public final class AccidentalCopy {}\n");

        BuildResult result = runner("build", "--stacktrace").buildAndFail();

        assertTrue(result.getOutput().contains("Plugin JAR bundles protected runtime classes"));
        assertTrue(result.getOutput().contains("net/minecraft/AccidentalCopy.class"));
    }

    private void prepareProject() throws Exception {
        Path idea = project.resolve(".idea/misc.xml");
        Files.createDirectories(idea.getParent());
        Files.writeString(idea, """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="ProjectRootManager" version="2" />
            </project>
            """);
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'sample'\n");
        Files.writeString(project.resolve("build.gradle"), """
            plugins {
                id 'dev.aerogel.plugin'
            }

            group = 'sample'
            version = '1.2.3'

            aerogel {
                minecraft.set('26.2')
                minecraftServerJar.set(layout.projectDirectory.file('fake-server.jar'))
                plugin {
                    id.set('sample_plugin')
                    name.set('Sample Plugin')
                    entrypoint('sample.ExamplePlugin')
                }
            }
            """);
        Path source = project.resolve("src/main/java/sample/ExamplePlugin.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package sample;

            import dev.aerogel.api.AerogelPlugin;
            import dev.aerogel.api.PluginContext;
            import dev.aerogel.api.event.EventHandler;
            import dev.aerogel.api.event.server.ServerStartedEvent;
            import net.minecraft.server.MinecraftServer;
            import net.minecraft.server.level.ServerPlayer;
            import net.minecraft.network.chat.Component;

            public final class ExamplePlugin implements AerogelPlugin {
                private MinecraftServer server;

                @Override
                public void onLoad(PluginContext context) {
                    context.events().listen(ServerStartedEvent.class, event -> server = event.server());
                    ServerPlayer player = null;
                    if (player != null) {
                        player.setDisplayName((Component) null);
                        player.setTabListName((Component) null);
                        player.setTabListHeaderFooter(
                            (Component) null, (Component) null);
                        player.sendTitle((Component) null);
                        player.resetCameraView();
                        player.resetWeather();
                        player.stopSounds();
                        player.clearViewOverrides();
                        player = player.respawn();
                    }
                }

                @EventHandler
                private void started(ServerStartedEvent event) {
                    server = event.server();
                }
            }
            """);
        Path mixin = project.resolve("src/main/mixins/ServerTick.mixin.kts");
        Files.createDirectories(mixin.getParent());
        Files.writeString(mixin, """
            import dev.aerogel.api.mixin.InjectionPoint
            import dev.aerogel.api.mixin.local
            import dev.aerogel.api.mixin.mixin
            import net.minecraft.server.MinecraftServer

            mixin<MinecraftServer> {
                inject(MinecraftServer::runServer, at = InjectionPoint.HEAD) { callback ->
                    check(!callback.isCancelled)
                }
                inject(
                    MinecraftServer::getServerModName,
                    at = InjectionPoint.HEAD,
                    cancellable = true
                ) { callback ->
                    callback.returnValue = "sample"
                }
            }
            """);
        Path syntheticMixin = project.resolve("src/main/mixins/SyntheticBrand.mixin.kts");
        Files.writeString(syntheticMixin, """
            import dev.aerogel.api.mixin.At
            import dev.aerogel.api.mixin.ConstantSelector
            import dev.aerogel.api.mixin.InjectionPoint
            import dev.aerogel.api.mixin.local
            import dev.aerogel.api.mixin.mixin
            import sample.fixture.BrandTarget
            import sample.fixture.BrandTarget.Product

            mixin<BrandTarget> {
                val bridgeField = accessor(BrandTarget::bridgeField)
                val hiddenLike = invoker(BrandTarget::hiddenLike)
                val uniqueCounter = uniqueField<Int>()
                val shadowStored = shadow(BrandTarget::shadowStored)
                val shadowMethod = shadow(BrandTarget::shadowMethod)

                injectConstructor(::BrandTarget, at = At.RETURN) { _ ->
                    constructed = "mixed"
                }

                inject(BrandTarget::brand, at = InjectionPoint.HEAD, cancellable = true) { callback ->
                    callback.returnValue = "generated"
                }
                modifyConstant<Int>(
                    BrandTarget::constant,
                    constant = ConstantSelector.value(5)
                ) { 7 }
                modifyArg<Int>(
                    BrandTarget::argument,
                    at = At.invoke(BrandTarget::doubleValue),
                    index = 0
                ) { value -> value + 1 }
                redirect(
                    BrandTarget::call,
                    BrandTarget::decorate
                ) { _, value -> "redirect:" + value }
                redirect(BrandTarget::make, ::Product) { value -> Product(value + 5) }
                overwrite(BrandTarget::overwriteMe) { "overwritten" }
                classInitializer(at = At.TAIL) {
                    BrandTarget.initialized = "initialized"
                }
                injectStatic(
                    BrandTarget::staticBrand,
                    at = At.HEAD,
                    cancellable = true
                ) { callback -> callback.returnValue = "static" }
                modifyConstantStatic<Int>(
                    BrandTarget::staticConstant,
                    constant = ConstantSelector.value(11)
                ) { 12 }
                modifyArgStatic<Int>(
                    BrandTarget::staticArgument,
                    at = At.invoke(BrandTarget::staticDouble),
                    index = 0
                ) { value -> value + 1 }
                redirectStatic(
                    BrandTarget::staticCall,
                    BrandTarget::staticDecorate
                ) { value -> "static-redirect:" + value }
                overwriteStatic(BrandTarget::staticOverwrite) { "static-overwrite" }
                redirectFieldGet(
                    BrandTarget::readField,
                    BrandTarget::stored
                ) { _ -> 9 }
                redirectFieldSet(
                    BrandTarget::writeField,
                    BrandTarget::stored
                ) { owner, value -> owner.stored = value * 2 }
                modifyArgs(
                    BrandTarget::args,
                    at = At.invoke(BrandTarget::combine)
                ) { args ->
                    args.set(0, 4)
                    args.set(1, 5)
                }
                modifyVariable<Int>(
                    BrandTarget::variable,
                    at = At.HEAD,
                    ordinal = 0,
                    argsOnly = true
                ) { value -> value + 10 }
                inject(BrandTarget::bridgeUse, at = At.HEAD, cancellable = true) { callback ->
                    bridgeField[this] = 6
                    callback.returnValue = hiddenLike(this, "ok") + ":" + bridgeField[this]
                }
                injectLocals(
                    BrandTarget::local,
                    at = At.RETURN.configured(ordinal = 1),
                    capture = local<String>(),
                    cancellable = true
                ) { callback, local -> callback.returnValue = local + ":captured" }
                inject(BrandTarget::uniqueUse, at = At.HEAD, cancellable = true) { callback ->
                    uniqueCounter[this] = uniqueCounter[this] + 1
                    callback.returnValue = uniqueCounter[this]
                }
                inject(BrandTarget::shadowUse, at = At.HEAD, cancellable = true) { callback ->
                    shadowStored[this] = 5
                    callback.returnValue = shadowMethod(this, "ok") + ":" + shadowStored[this]
                }
            }
            """);
        createFakeBundler(project.resolve("fake-server.jar"));
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
            .withProjectDir(project.toFile())
            .withArguments(arguments)
            .withPluginClasspath();
    }

    private static void createFakeBundler(Path bundler) throws Exception {
        Path fixture = bundler.getParent().resolve("fixture");
        Path source = fixture.resolve("src/net/minecraft/server/MinecraftServer.java");
        Path player = fixture.resolve("src/net/minecraft/server/level/ServerPlayer.java");
        Path component = fixture.resolve("src/net/minecraft/network/chat/Component.java");
        Path scheduler = fixture.resolve("src/net/minecraft/util/thread/TaskScheduler.java");
        Path brandTarget = fixture.resolve("src/sample/fixture/BrandTarget.java");
        Path classes = fixture.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source,
            "package net.minecraft.server; public class MinecraftServer implements "
                + "net.minecraft.util.thread.TaskScheduler { public void runServer() {} "
                + "public String getServerModName() { return \"vanilla\"; } }\n", StandardCharsets.UTF_8);
        Files.createDirectories(player.getParent());
        Files.createDirectories(component.getParent());
        Files.createDirectories(scheduler.getParent());
        Files.createDirectories(brandTarget.getParent());
        Files.writeString(player,
            "package net.minecraft.server.level; public class ServerPlayer {}\n", StandardCharsets.UTF_8);
        Files.writeString(component,
            "package net.minecraft.network.chat; public interface Component {}\n", StandardCharsets.UTF_8);
        Files.writeString(scheduler,
            "package net.minecraft.util.thread; public interface TaskScheduler extends AutoCloseable {"
                + " default void close() {} }\n", StandardCharsets.UTF_8);
        Files.writeString(brandTarget, """
            package sample.fixture;
            public class BrandTarget {
                public BrandTarget() {}
                public String constructed = "base";
                public static String initialized = "base";
                public static String staticBrand() { return "base"; }
                public static int staticConstant() { return 11; }
                public static int staticArgument() { return staticDouble(3); }
                public static int staticDouble(int value) { return value * 2; }
                public static String staticCall(String value) { return staticDecorate(value); }
                public static String staticDecorate(String value) { return "static-base:" + value; }
                public static String staticOverwrite() { return "base"; }
                public String brand() { return "base"; }
                public int constant() { return 5; }
                public int argument() { return doubleValue(3); }
                public int doubleValue(int value) { return value * 2; }
                public String call(String value) { return decorate(value); }
                public String decorate(String value) { return "base:" + value; }
                public String overwriteMe() { return "base"; }
                public int stored = 3;
                public int readField() { return stored; }
                public void writeField(int value) { stored = value; }
                public int args() { return combine(1, 2); }
                public int combine(int first, int second) { return first + second; }
                public int variable(int value) { return value; }
                public int bridgeField = 2;
                public String hiddenLike(String value) { return "hidden:" + value; }
                public String bridgeUse() { return "base"; }
                public String local() {
                    String local = "local";
                    if (local.isEmpty()) return "empty";
                    return local;
                }
                public int uniqueUse() { return -1; }
                public int shadowStored = 1;
                public String shadowMethod(String value) { return "shadow:" + value; }
                public String shadowUse() { return "base"; }
                public Product make() { return new Product(2); }
                public static class Product {
                    public final int value;
                    public Product(int value) { this.value = value; }
                }
            }
            """, StandardCharsets.UTF_8);
        int compilation = ToolProvider.getSystemJavaCompiler().run(
            null, null, null, "--release", "25", "-d", classes.toString(),
            source.toString(), player.toString(), component.toString(), scheduler.toString(), brandTarget.toString());
        assertEquals(0, compilation);

        Path server = fixture.resolve("server.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(server))) {
            Path classFile = classes.resolve("net/minecraft/server/MinecraftServer.class");
            entry(output, "net/minecraft/server/MinecraftServer.class", Files.readAllBytes(classFile));
            Path playerClass = classes.resolve("net/minecraft/server/level/ServerPlayer.class");
            entry(output, "net/minecraft/server/level/ServerPlayer.class", Files.readAllBytes(playerClass));
            Path componentClass = classes.resolve("net/minecraft/network/chat/Component.class");
            entry(output, "net/minecraft/network/chat/Component.class", Files.readAllBytes(componentClass));
            Path schedulerClass = classes.resolve("net/minecraft/util/thread/TaskScheduler.class");
            entry(output, "net/minecraft/util/thread/TaskScheduler.class", Files.readAllBytes(schedulerClass));
            Path brandTargetClass = classes.resolve("sample/fixture/BrandTarget.class");
            entry(output, "sample/fixture/BrandTarget.class", Files.readAllBytes(brandTargetClass));
            Path productClass = classes.resolve("sample/fixture/BrandTarget$Product.class");
            entry(output, "sample/fixture/BrandTarget$Product.class", Files.readAllBytes(productClass));
        }
        String versions = Hashing.sha256(server) + "\t26.2\t26.2/server.jar\n";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(bundler))) {
            entry(output, "META-INF/versions.list", versions.getBytes(StandardCharsets.UTF_8));
            entry(output, "META-INF/libraries.list", new byte[0]);
            entry(output, "META-INF/main-class", "net.minecraft.server.Main\n".getBytes(StandardCharsets.UTF_8));
            entry(output, "META-INF/versions/26.2/server.jar", Files.readAllBytes(server));
        }
    }

    private static void entry(JarOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(content);
        output.closeEntry();
    }

    private static void assertGeneratedMixinApplies(Path pluginJar, Path targetJar) throws Exception {
        // Mixin keeps class metadata for the lifetime of the test JVM. On Windows this can
        // keep the backing JAR open past @TempDir cleanup, so exercise disposable copies
        // under Gradle's regular build output instead of the JUnit-owned project directory.
        Path runtime = Path.of(System.getProperty("user.dir"), "build", "test-mixin-runtime",
            UUID.randomUUID().toString());
        Files.createDirectories(runtime);
        Path runtimePlugin = Files.copy(pluginJar, runtime.resolve("plugin.jar"));
        Path runtimeTarget = Files.copy(targetJar, runtime.resolve("target.jar"));
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (TransformingClassLoader loader = new TransformingClassLoader(
            new URL[]{runtimeTarget.toUri().toURL(), runtimePlugin.toUri().toURL()}, previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            PluginDescriptor plugin = new PluginDescriptor(
                runtimePlugin,
                "sample_plugin",
                "1.2.3",
                "Sample Plugin",
                ">=26.2",
                List.of("sample.ExamplePlugin"),
                List.of("sample_plugin.generated.mixins.json"),
                Map.of()
            );
            MixinBootstrapper.initialize(loader, List.of(plugin));
            Class<?> type = Class.forName("sample.fixture.BrandTarget", true, loader);
            Object target = type.getConstructor().newInstance();
            assertEquals("mixed", type.getField("constructed").get(target));
            assertEquals("generated", type.getMethod("brand").invoke(target));
            assertEquals(7, type.getMethod("constant").invoke(target));
            assertEquals(8, type.getMethod("argument").invoke(target));
            assertEquals("redirect:value", type.getMethod("call", String.class).invoke(target, "value"));
            assertEquals("overwritten", type.getMethod("overwriteMe").invoke(target));
            assertEquals("initialized", type.getField("initialized").get(null));
            assertEquals("static", type.getMethod("staticBrand").invoke(null));
            assertEquals(12, type.getMethod("staticConstant").invoke(null));
            assertEquals(8, type.getMethod("staticArgument").invoke(null));
            assertEquals("static-redirect:value", type.getMethod("staticCall", String.class).invoke(null, "value"));
            assertEquals("static-overwrite", type.getMethod("staticOverwrite").invoke(null));
            assertEquals(9, type.getMethod("readField").invoke(target));
            type.getMethod("writeField", int.class).invoke(target, 4);
            assertEquals(8, type.getField("stored").get(target));
            assertEquals(9, type.getMethod("args").invoke(target));
            assertEquals(13, type.getMethod("variable", int.class).invoke(target, 3));
            assertEquals("hidden:ok:6", type.getMethod("bridgeUse").invoke(target));
            assertEquals("local:captured", type.getMethod("local").invoke(target));
            assertEquals(1, type.getMethod("uniqueUse").invoke(target));
            assertEquals(2, type.getMethod("uniqueUse").invoke(target));
            assertEquals("shadow:ok:5", type.getMethod("shadowUse").invoke(target));
            Object product = type.getMethod("make").invoke(target);
            assertEquals(7, product.getClass().getField("value").get(product));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
