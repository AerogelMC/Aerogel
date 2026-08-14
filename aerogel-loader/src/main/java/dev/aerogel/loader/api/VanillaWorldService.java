package dev.aerogel.loader.api;

import dev.aerogel.api.world.VanillaDimension;
import dev.aerogel.api.world.WorldService;
import dev.aerogel.loader.internal.MinecraftServerWorldBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Vanilla-backed dynamic world service. Private lifecycle state is owned by a Mixin bridge. */
final class VanillaWorldService implements WorldService {
    private final PluginApiScope scope;

    VanillaWorldService(PluginApiScope scope) {
        this.scope = scope;
    }

    @Override
    public Optional<ServerLevel> find(String id) {
        return Optional.ofNullable(server().getLevel(levelKey(id)));
    }

    @Override
    public boolean unload(String id) {
        return bridge().aerogel$unloadLevel(levelKey(id));
    }

    @Override
    public boolean delete(String id) {
        ResourceKey<Level> key = levelKey(id);
        MinecraftServerWorldBridge bridge = bridge();
        Path worldRoot = bridge.aerogel$worldDirectory().toAbsolutePath().normalize();
        Path dimensionDirectory = bridge.aerogel$dimensionDirectory(key)
            .toAbsolutePath().normalize();

        boolean unloaded = bridge.aerogel$unloadLevel(key);
        if (!Files.exists(dimensionDirectory)) return unloaded;
        deleteTree(worldRoot, dimensionDirectory);
        return true;
    }

    @Override
    public ServerLevel createFlat(String id) {
        return createFlat(id, serverSeed());
    }

    @Override
    public ServerLevel createFlat(String id, long seed) {
        RegistryAccess registries = server().registryAccess();
        Registry<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        Registry<StructureSet> structures = registries.lookupOrThrow(Registries.STRUCTURE_SET);
        Registry<PlacedFeature> placedFeatures = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        FlatLevelGeneratorSettings settings = FlatLevelGeneratorSettings.getDefault(
            biomes, structures, placedFeatures);
        return createFlat(id, seed, settings);
    }

    @Override
    public ServerLevel createFlat(String id, FlatLevelGeneratorSettings settings) {
        return createFlat(id, serverSeed(), settings);
    }

    @Override
    public ServerLevel createFlat(
        String id, long seed, FlatLevelGeneratorSettings settings
    ) {
        if (settings == null) throw new IllegalArgumentException("Flat settings must not be null");
        return create(id, seed, VanillaDimension.OVERWORLD, new FlatLevelSource(settings));
    }

    @Override
    public ServerLevel createVoid(String id) {
        return createVoid(id, serverSeed());
    }

    @Override
    public ServerLevel createVoid(String id, long seed) {
        Registry<Biome> biomes = server().registryAccess().lookupOrThrow(Registries.BIOME);
        Holder.Reference<Biome> voidBiome = biomes.getOrThrow(Biomes.THE_VOID);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
            Optional.empty(), voidBiome, List.of());
        settings.updateLayers();
        return createFlat(id, seed, settings);
    }

    @Override
    public ServerLevel createVanilla(String id, VanillaDimension dimension) {
        return createVanilla(id, serverSeed(), dimension);
    }

    @Override
    public ServerLevel createVanilla(
        String id, long seed, VanillaDimension dimension
    ) {
        LevelStem stem = vanillaStem(dimension);
        return createWorld(id, seed, stem);
    }

    @Override
    public ServerLevel create(String id, ChunkGenerator generator) {
        return create(id, serverSeed(), generator);
    }

    @Override
    public ServerLevel create(String id, long seed, ChunkGenerator generator) {
        return create(id, seed, VanillaDimension.OVERWORLD, generator);
    }

    @Override
    public ServerLevel create(
        String id, VanillaDimension dimension, ChunkGenerator generator
    ) {
        return create(id, serverSeed(), dimension, generator);
    }

    @Override
    public ServerLevel create(
        String id, long seed, VanillaDimension dimension, ChunkGenerator generator
    ) {
        if (generator == null) throw new IllegalArgumentException("Chunk generator must not be null");
        LevelStem vanillaStem = vanillaStem(dimension);
        return createWorld(id, seed, new LevelStem(vanillaStem.type(), generator));
    }

    private ServerLevel createWorld(String id, long seed, LevelStem stem) {
        MinecraftServer server = server();
        requireServerThread(server);
        ResourceKey<Level> key = levelKey(id);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            verifyWorld(existing, stem, id);
            return existing;
        }
        return bridge().aerogel$createLevel(key, stem, seed);
    }

    private LevelStem vanillaStem(VanillaDimension dimension) {
        if (dimension == null) throw new IllegalArgumentException("Vanilla dimension must not be null");
        Registry<LevelStem> stems = server().registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        ResourceKey<LevelStem> key = switch (dimension) {
            case OVERWORLD -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case END -> LevelStem.END;
        };
        LevelStem stem = stems.getValue(key);
        if (stem == null) {
            throw new IllegalStateException("Vanilla " + dimension + " stem is unavailable");
        }
        return stem;
    }

    private ResourceKey<Level> levelKey(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(qualifiedId(id)));
    }

    private String qualifiedId(String id) {
        return qualifiedId(scope.pluginId(), id);
    }

    private long serverSeed() {
        return server().getWorldGenSettings().options().seed();
    }

    private MinecraftServer server() {
        return (MinecraftServer) scope.serverHandle();
    }

    private MinecraftServerWorldBridge bridge() {
        MinecraftServer server = server();
        if (!(server instanceof MinecraftServerWorldBridge bridge)) {
            throw new IllegalStateException("Minecraft world lifecycle bridge is unavailable");
        }
        return bridge;
    }

    private void verifyWorld(ServerLevel level, LevelStem requested, String id) {
        ChunkGenerator loadedGenerator = level.getChunkSource().getGenerator();
        if (!loadedGenerator.getClass().getName().equals(requested.generator().getClass().getName())) {
            throw new IllegalStateException(
                "World " + qualifiedId(id) + " is already loaded with "
                    + loadedGenerator.getClass().getName() + ", not "
                    + requested.generator().getClass().getName());
        }
        if (!level.dimensionTypeRegistration().equals(requested.type())) {
            throw new IllegalStateException(
                "World " + qualifiedId(id) + " is already loaded with a different dimension type");
        }
    }

    static String qualifiedId(String pluginId, String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("World id must not be blank");
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? pluginId + ':' + normalized : normalized;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("World creation must run on the Minecraft server thread");
        }
    }

    static void deleteTree(Path worldRoot, Path target) {
        Path normalizedRoot = worldRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (normalizedTarget.equals(normalizedRoot) || !normalizedTarget.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                "Refusing to delete a world directory outside the primary world: " + normalizedTarget);
        }
        try {
            Files.walkFileTree(normalizedTarget, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes
                ) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(
                    Path directory, IOException failure
                ) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Could not delete world directory " + normalizedTarget, exception);
        }
    }
}
