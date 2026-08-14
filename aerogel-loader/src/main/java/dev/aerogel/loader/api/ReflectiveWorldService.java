package dev.aerogel.loader.api;

import dev.aerogel.api.world.WorldService;
import dev.aerogel.api.world.VanillaDimension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Runtime bridge for vanilla's dynamic ServerLevel construction. */
final class ReflectiveWorldService implements WorldService {
    private final PluginApiScope scope;

    ReflectiveWorldService(PluginApiScope scope) {
        this.scope = scope;
    }

    @Override public Optional<ServerLevel> find(String id) {
        Object server = scope.serverHandle();
        Object level = Reflect.invoke(server, "getLevel", levelKey(server, id));
        return Optional.ofNullable((ServerLevel) level);
    }

    @Override public ServerLevel createFlat(String id) {
        Object server = scope.serverHandle();
        Object settings = Reflect.field(server, "worldGenSettings");
        Object options = Reflect.invoke(settings, "options");
        long seed = ((Number) Reflect.invoke(options, "seed")).longValue();
        return createFlat(id, seed);
    }

    @Override public ServerLevel createFlat(String id, long seed) {
        Object server = scope.serverHandle();
        requireServerThread(server);
        ClassLoader loader = scope.loader();
        Class<?> registries = Reflect.type(loader, "net.minecraft.core.registries.Registries");
        Object registryAccess = Reflect.invoke(server, "registryAccess");
        Object biomes = Reflect.invoke(
            registryAccess, "lookupOrThrow", Reflect.staticField(registries, "BIOME"));
        Object structures = Reflect.invoke(
            registryAccess, "lookupOrThrow", Reflect.staticField(registries, "STRUCTURE_SET"));
        Object placedFeatures = Reflect.invoke(
            registryAccess, "lookupOrThrow", Reflect.staticField(registries, "PLACED_FEATURE"));
        Class<?> flatSettingsType = Reflect.type(
            loader, "net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings");
        Object flatSettings = Reflect.invokeStatic(
            flatSettingsType, "getDefault", biomes, structures, placedFeatures);
        return createFlat(id, seed, (FlatLevelGeneratorSettings) flatSettings);
    }

    @Override public ServerLevel createFlat(String id, FlatLevelGeneratorSettings settings) {
        return createFlat(id, serverSeed(), settings);
    }

    @Override public ServerLevel createFlat(
        String id, long seed, FlatLevelGeneratorSettings settings
    ) {
        if (settings == null) throw new IllegalArgumentException("Flat settings must not be null");
        Object generator = Reflect.construct(
            Reflect.type(scope.loader(), "net.minecraft.world.level.levelgen.FlatLevelSource"), settings);
        return create(id, seed, VanillaDimension.OVERWORLD, (ChunkGenerator) generator);
    }

    @Override public ServerLevel createVoid(String id) {
        return createVoid(id, serverSeed());
    }

    @Override public ServerLevel createVoid(String id, long seed) {
        Object server = scope.serverHandle();
        requireServerThread(server);
        ClassLoader loader = scope.loader();
        Class<?> registries = Reflect.type(loader, "net.minecraft.core.registries.Registries");
        Object registryAccess = Reflect.invoke(server, "registryAccess");
        Object biomes = Reflect.invoke(
            registryAccess, "lookupOrThrow", Reflect.staticField(registries, "BIOME"));
        Object voidBiomeKey = Reflect.staticField(
            Reflect.type(loader, "net.minecraft.world.level.biome.Biomes"), "THE_VOID");
        Object voidBiome = Reflect.invoke(biomes, "getOrThrow", voidBiomeKey);
        Object settings = Reflect.construct(
            Reflect.type(loader, "net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings"),
            Optional.empty(), voidBiome, List.of());
        Reflect.invoke(settings, "updateLayers");
        return createFlat(id, seed, (FlatLevelGeneratorSettings) settings);
    }

    @Override public ServerLevel createVanilla(String id, VanillaDimension dimension) {
        return createVanilla(id, serverSeed(), dimension);
    }

    @Override public ServerLevel createVanilla(
        String id, long seed, VanillaDimension dimension
    ) {
        Object stem = vanillaStem(scope.serverHandle(), dimension);
        return createWorld(
            id,
            seed,
            Reflect.invoke(stem, "type"),
            (ChunkGenerator) Reflect.invoke(stem, "generator"));
    }

    @Override public ServerLevel create(String id, ChunkGenerator generator) {
        return create(id, serverSeed(), generator);
    }

    @Override public ServerLevel create(String id, long seed, ChunkGenerator generator) {
        return create(id, seed, VanillaDimension.OVERWORLD, generator);
    }

    @Override public ServerLevel create(
        String id, VanillaDimension dimension, ChunkGenerator generator
    ) {
        return create(id, serverSeed(), dimension, generator);
    }

    @Override public ServerLevel create(
        String id, long seed, VanillaDimension dimension, ChunkGenerator generator
    ) {
        Object stem = vanillaStem(scope.serverHandle(), dimension);
        return createWorld(id, seed, Reflect.invoke(stem, "type"), generator);
    }

    private ServerLevel createWorld(
        String id, long seed, Object dimensionType, ChunkGenerator generator
    ) {
        if (generator == null) throw new IllegalArgumentException("Chunk generator must not be null");
        Object server = scope.serverHandle();
        requireServerThread(server);
        Object levelKey = levelKey(server, id);
        Object existing = Reflect.invoke(server, "getLevel", levelKey);
        if (existing != null) {
            verifyWorld(existing, dimensionType, generator, id);
            return (ServerLevel) existing;
        }

        ClassLoader loader = scope.loader();
        Class<?> levelStemType = Reflect.type(loader, "net.minecraft.world.level.dimension.LevelStem");
        Object levelStem = Reflect.construct(levelStemType, dimensionType, generator);

        Object worldData = Reflect.field(server, "worldData");
        Object levelData = Reflect.construct(
            Reflect.type(loader, "net.minecraft.world.level.storage.DerivedLevelData"),
            worldData, Reflect.invoke(worldData, "overworldData"));
        long biomeSeed = ((Number) Reflect.invokeStatic(
            Reflect.type(loader, "net.minecraft.world.level.biome.BiomeManager"),
            "obfuscateSeed", seed)).longValue();
        boolean debug = (Boolean) Reflect.invoke(worldData, "isDebugWorld");
        Object level = Reflect.construct(
            Reflect.type(loader, "net.minecraft.server.level.ServerLevel"),
            server,
            Reflect.field(server, "executor"),
            Reflect.field(server, "storageSource"),
            levelData,
            levelKey,
            levelStem,
            debug,
            biomeSeed,
            List.of(),
            false);

        @SuppressWarnings("unchecked")
        Map<Object, Object> levels = (Map<Object, Object>) Reflect.field(server, "levels");
        levels.put(levelKey, level);
        try {
            Object border = Reflect.invoke(level, "getWorldBorder");
            Reflect.invoke(border, "setAbsoluteMaxSize", Reflect.invoke(server, "getAbsoluteMaxWorldSize"));
            Reflect.invoke(Reflect.invoke(server, "getPlayerList"), "addWorldborderListener", level);
            return (ServerLevel) level;
        } catch (RuntimeException exception) {
            levels.remove(levelKey, level);
            try { Reflect.invoke(level, "close"); }
            catch (RuntimeException suppressed) { exception.addSuppressed(suppressed); }
            throw exception;
        }
    }

    private Object levelKey(Object server, String id) {
        ClassLoader loader = server.getClass().getClassLoader();
        String value = qualifiedId(id);
        Object identifier = Reflect.invokeStatic(
            Reflect.type(loader, "net.minecraft.resources.Identifier"), "parse", value);
        Object dimensionRegistry = Reflect.staticField(
            Reflect.type(loader, "net.minecraft.core.registries.Registries"), "DIMENSION");
        return Reflect.invokeStatic(
            Reflect.type(loader, "net.minecraft.resources.ResourceKey"),
            "create", dimensionRegistry, identifier);
    }

    private String qualifiedId(String id) {
        return qualifiedId(scope.pluginId(), id);
    }

    private long serverSeed() {
        Object settings = Reflect.field(scope.serverHandle(), "worldGenSettings");
        Object options = Reflect.invoke(settings, "options");
        return ((Number) Reflect.invoke(options, "seed")).longValue();
    }

    private Object vanillaStem(Object server, VanillaDimension dimension) {
        if (dimension == null) throw new IllegalArgumentException("Vanilla dimension must not be null");
        ClassLoader loader = scope.loader();
        Class<?> levelStemType = Reflect.type(loader, "net.minecraft.world.level.dimension.LevelStem");
        Object registryAccess = Reflect.invoke(server, "registryAccess");
        Class<?> registries = Reflect.type(loader, "net.minecraft.core.registries.Registries");
        Object stems = Reflect.invoke(
            registryAccess, "lookupOrThrow", Reflect.staticField(registries, "LEVEL_STEM"));
        Object key = Reflect.staticField(levelStemType, switch (dimension) {
            case OVERWORLD -> "OVERWORLD";
            case NETHER -> "NETHER";
            case END -> "END";
        });
        Object stem = Reflect.invoke(stems, "getValue", key);
        if (stem == null) throw new IllegalStateException("Vanilla " + dimension + " stem is unavailable");
        return stem;
    }

    static String qualifiedId(String pluginId, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("World id must not be blank");
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? pluginId + ':' + normalized : normalized;
    }

    private static void requireServerThread(Object server) {
        if (!(Boolean) Reflect.invoke(server, "isSameThread")) {
            throw new IllegalStateException("World creation must run on the Minecraft server thread");
        }
    }

    private void verifyWorld(
        Object level, Object requestedDimensionType, Object requestedGenerator, String id
    ) {
        Object loadedGenerator = Reflect.invoke(Reflect.invoke(level, "getChunkSource"), "getGenerator");
        if (!loadedGenerator.getClass().getName().equals(requestedGenerator.getClass().getName())) {
            throw new IllegalStateException(
                "World " + qualifiedId(id) + " is already loaded with "
                    + loadedGenerator.getClass().getName() + ", not "
                    + requestedGenerator.getClass().getName());
        }
        Object loadedDimensionType = Reflect.invoke(level, "dimensionTypeRegistration");
        if (!loadedDimensionType.equals(requestedDimensionType)) {
            throw new IllegalStateException(
                "World " + qualifiedId(id) + " is already loaded with a different dimension type");
        }
    }
}
