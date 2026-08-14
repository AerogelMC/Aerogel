package dev.aerogel.api.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.Optional;

/** Creates and locates server levels owned by the running Minecraft server. */
public interface WorldService {
    /** Finds a loaded level. An unqualified id uses the calling plugin's namespace. */
    Optional<ServerLevel> find(String id);

    /** Saves and unloads a dynamic level. Returns false when the level is not loaded. */
    boolean unload(String id);

    /** Unloads a dynamic level and permanently deletes its saved dimension directory. */
    boolean delete(String id);

    /** Creates the default vanilla flat world using the server's world seed. */
    ServerLevel createFlat(String id);

    /** Creates the default vanilla flat world using an explicit seed. */
    ServerLevel createFlat(String id, long seed);

    /** Creates a flat world from Minecraft's complete superflat settings. */
    ServerLevel createFlat(String id, FlatLevelGeneratorSettings settings);

    /** Creates a flat world from Minecraft's complete superflat settings and explicit seed. */
    ServerLevel createFlat(String id, long seed, FlatLevelGeneratorSettings settings);

    /** Creates an empty overworld-type level using the server's world seed. */
    ServerLevel createVoid(String id);

    /** Creates an empty overworld-type level using an explicit seed. */
    ServerLevel createVoid(String id, long seed);

    /** Creates a new level using one of Minecraft's built-in dimension generators. */
    ServerLevel createVanilla(String id, VanillaDimension dimension);

    /** Creates a new level using one of Minecraft's built-in dimension generators and explicit seed. */
    ServerLevel createVanilla(String id, long seed, VanillaDimension dimension);

    /** Creates a world backed directly by a vanilla-compatible chunk generator. */
    ServerLevel create(String id, ChunkGenerator generator);

    /** Creates a world backed directly by a vanilla-compatible chunk generator and explicit seed. */
    ServerLevel create(String id, long seed, ChunkGenerator generator);

    /** Creates a custom-generated world with a selected built-in dimension type. */
    ServerLevel create(String id, VanillaDimension dimension, ChunkGenerator generator);

    /** Creates a custom-generated world with an explicit seed and selected built-in dimension type. */
    ServerLevel create(
        String id, long seed, VanillaDimension dimension, ChunkGenerator generator
    );
}
