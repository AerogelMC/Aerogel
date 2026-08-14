package dev.aerogel.loader.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.aerogel.api.storage.DataCodec;
import dev.aerogel.api.storage.DataFile;
import dev.aerogel.api.storage.StorageOptions;
import dev.aerogel.api.storage.TypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.function.Function;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedStorageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndReloadsTypedJson() throws Exception {
        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            DataFile<Profile> file = scope.storage().json(
                Path.of("profiles", "owner.json"), Profile.class, () -> new Profile("Steve", 0));

            assertEquals(new Profile("Steve", 0), file.load().get(5, TimeUnit.SECONDS));
            file.set(new Profile("Alex", 42));
            file.flush().get(5, TimeUnit.SECONDS);
            file.close();

            DataFile<Profile> reopened = scope.storage().json(
                Path.of("profiles", "owner.json"), Profile.class, () -> new Profile("Nobody", -1));
            assertEquals(new Profile("Alex", 42), reopened.load().get(5, TimeUnit.SECONDS));
            assertFalse(reopened.dirty());
        }
    }

    @Test
    void supportsGenericCollections() throws Exception {
        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            DataFile<Map<String, Profile>> file = scope.storage().json(
                Path.of("players.json"),
                new TypeRef<Map<String, Profile>>() { },
                LinkedHashMap::new
            );
            file.load().get(5, TimeUnit.SECONDS);
            file.edit(values -> values.put("one", new Profile("Player", 7)));
            file.flush().get(5, TimeUnit.SECONDS);
            file.close();

            DataFile<Map<String, Profile>> reopened = scope.storage().json(
                Path.of("players.json"),
                new TypeRef<Map<String, Profile>>() { },
                LinkedHashMap::new
            );
            assertEquals(
                Map.of("one", new Profile("Player", 7)),
                reopened.load().get(5, TimeUnit.SECONDS)
            );
        }
    }

    @Test
    void coalescesRapidChangesIntoOneWrite() throws Exception {
        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            AtomicInteger encodes = new AtomicInteger();
            DataCodec<Integer> codec = new DataCodec<>() {
                @Override public byte[] encode(Integer value) {
                    encodes.incrementAndGet();
                    return value.toString().getBytes(StandardCharsets.UTF_8);
                }

                @Override public Integer decode(byte[] encoded) {
                    return Integer.valueOf(new String(encoded, StandardCharsets.UTF_8));
                }
            };
            StorageOptions options = StorageOptions.defaults().withSaveDelay(Duration.ofMinutes(1));
            DataFile<Integer> file = scope.storage().open(Path.of("counter.dat"), codec, () -> 0, options);
            file.load().get(5, TimeUnit.SECONDS);

            for (int value = 1; value <= 100; value++) file.set(value);
            file.flush().get(5, TimeUnit.SECONDS);

            assertEquals(1, encodes.get());
            assertEquals("100", Files.readString(file.path(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void automaticallySavesWithoutAnExplicitFlush() throws Exception {
        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            DataFile<Profile> file = scope.storage().json(
                Path.of("automatic.json"),
                Profile.class,
                () -> new Profile("Initial", 0),
                StorageOptions.defaults().withSaveDelay(Duration.ZERO)
            );
            file.load().get(5, TimeUnit.SECONDS);
            file.set(new Profile("Automatic", 12));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            String saved = "";
            while (System.nanoTime() < deadline) {
                if (Files.isRegularFile(file.path())) {
                    saved = Files.readString(file.path(), StandardCharsets.UTF_8);
                    if (saved.contains("Automatic")) break;
                }
                Thread.sleep(10L);
            }

            assertTrue(saved.contains("Automatic"));
            assertFalse(file.dirty());
        }
    }

    @Test
    void scopeCloseFlushesDirtyFiles() throws Exception {
        AerogelApiRuntime runtime = new AerogelApiRuntime();
        PluginApiScope scope = scope(runtime);
        DataFile<Profile> file = scope.storage().json(
            Path.of("shutdown.json"),
            TypeRef.of(Profile.class),
            () -> new Profile("Before", 0),
            StorageOptions.defaults().withSaveDelay(Duration.ofHours(1))
        );
        file.load().get(5, TimeUnit.SECONDS);
        file.set(new Profile("Saved", 9));

        scope.close();

        String saved = Files.readString(temporaryDirectory.resolve("plugin/shutdown.json"), StandardCharsets.UTF_8);
        assertTrue(saved.contains("Saved"));
        assertFalse(file.active());
        runtime.close();
    }

    @Test
    void refusesPathsOutsidePluginDataDirectory() {
        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);

            assertThrows(IllegalArgumentException.class, () -> scope.storage().json(
                Path.of("..", "outside.json"), Profile.class, () -> new Profile("No", 0)));
            assertFalse(Files.exists(temporaryDirectory.resolve("outside.json")));
        }
    }

    @Test
    void corruptJsonFailsWithoutOverwritingTheFile() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("plugin");
        Files.createDirectories(dataDirectory);
        Path path = dataDirectory.resolve("broken.json");
        Files.writeString(path, "{ definitely not json", StandardCharsets.UTF_8);

        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            DataFile<Profile> file = scope.storage().json(
                Path.of("broken.json"), Profile.class, () -> new Profile("Default", 0));

            assertThrows(ExecutionException.class, () -> file.load().get(5, TimeUnit.SECONDS));
            assertTrue(file.lastFailure().isPresent());
        }

        assertEquals("{ definitely not json", Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void preventsTwoManagersForTheSamePath() throws Exception {
        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            DataFile<Profile> first = scope.storage().json(
                Path.of("same.json"), Profile.class, () -> new Profile("First", 1));
            first.load().get(5, TimeUnit.SECONDS);

            assertThrows(IllegalStateException.class, () -> scope.storage().json(
                Path.of("same.json"), Profile.class, () -> new Profile("Second", 2)));

            first.close();
            DataFile<Profile> second = scope.storage().json(
                Path.of("same.json"), Profile.class, () -> new Profile("Second", 2));
            assertEquals(new Profile("First", 1), second.load().get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void registryAwareJsonWaitsForTheServerAndThenRoundTrips() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("plugin");
        Files.createDirectories(dataDirectory);
        Files.writeString(dataDirectory.resolve("minecraft.json"), "7", StandardCharsets.UTF_8);

        try (AerogelApiRuntime runtime = new AerogelApiRuntime()) {
            PluginApiScope scope = scope(runtime);
            DataFile<Integer> file = scope.storage().codecJson(
                "minecraft.json", integerCodec(), () -> 0);

            assertFalse(file.loaded());
            runtime.attach(new TestServer());
            assertEquals(7, file.load().get(5, TimeUnit.SECONDS));

            file.set(9);
            file.flush().get(5, TimeUnit.SECONDS);
            assertEquals("9", Files.readString(file.path(), StandardCharsets.UTF_8).trim());
        }
    }

    @Test
    void closingBeforeServerReadyDoesNotWaitForARegistry() {
        AerogelApiRuntime runtime = new AerogelApiRuntime();
        PluginApiScope scope = scope(runtime);
        DataFile<Integer> file = scope.storage().codecJson(
            "never-started.json", integerCodec(), () -> 0);

        scope.close();

        assertFalse(file.active());
        assertTrue(file.load().isCompletedExceptionally());
        runtime.close();
    }

    private PluginApiScope scope(AerogelApiRuntime runtime) {
        return runtime.openScope(
            "test_plugin",
            Logger.getLogger("test storage"),
            ManagedStorageServiceTest.class.getClassLoader(),
            temporaryDirectory.resolve("plugin")
        );
    }

    private record Profile(String name, int coins) { }

    private static Codec<Integer> integerCodec() {
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encodeStart(DynamicOps<T> operations, Integer input) {
                @SuppressWarnings("unchecked")
                T encoded = (T) IntTag.valueOf(input);
                return new Success<>(encoded);
            }

            @Override
            public <T> DataResult<Integer> parse(DynamicOps<T> operations, T input) {
                return new Success<>(((Tag) input).asNumber().orElseThrow().intValue());
            }

            @Override
            public Codec<java.util.List<Integer>> listOf() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private record Success<R>(R value) implements DataResult<R> {
        @Override
        public <E extends Throwable> R getOrThrow(Function<String, E> exceptionFactory) {
            return value;
        }
    }

    private static final class TestServer extends MinecraftServer {
        private final RegistryAccess.Frozen registries = new RegistryAccess.Frozen() { };

        @Override
        public RegistryAccess.Frozen registryAccess() {
            return registries;
        }
    }
}
