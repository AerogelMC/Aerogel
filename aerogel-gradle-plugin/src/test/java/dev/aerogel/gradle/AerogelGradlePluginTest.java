package dev.aerogel.gradle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
            assertNotNull(jar.getJarEntry("aerogel.plugin.json"));
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
        }
        assertTrue(Files.isRegularFile(project.resolve(
            "build/aerogel/minecraft/26.2/classpath/versions/26.2/server.jar")));
        Path developmentServer = project.resolve(
            "build/aerogel/minecraft/26.2/classpath/versions/26.2/server.jar");
        try (JarFile jar = new JarFile(developmentServer.toFile())) {
            byte[] scheduler = jar.getInputStream(jar.getJarEntry(
                "net/minecraft/util/thread/TaskScheduler.class")).readAllBytes();
            assertFalse(java.util.List.of(new ClassReader(scheduler).getInterfaces())
                .contains("java/lang/AutoCloseable"));
        }
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
                    }
                }

                @EventHandler
                private void started(ServerStartedEvent event) {
                    server = event.server();
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
        Path classes = fixture.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source,
            "package net.minecraft.server; public class MinecraftServer implements "
                + "net.minecraft.util.thread.TaskScheduler {}\n", StandardCharsets.UTF_8);
        Files.createDirectories(player.getParent());
        Files.createDirectories(component.getParent());
        Files.createDirectories(scheduler.getParent());
        Files.writeString(player,
            "package net.minecraft.server.level; public class ServerPlayer {}\n", StandardCharsets.UTF_8);
        Files.writeString(component,
            "package net.minecraft.network.chat; public interface Component {}\n", StandardCharsets.UTF_8);
        Files.writeString(scheduler,
            "package net.minecraft.util.thread; public interface TaskScheduler extends AutoCloseable {"
                + " default void close() {} }\n", StandardCharsets.UTF_8);
        int compilation = ToolProvider.getSystemJavaCompiler().run(
            null, null, null, "--release", "25", "-d", classes.toString(),
            source.toString(), player.toString(), component.toString(), scheduler.toString());
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
}
