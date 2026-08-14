package dev.aerogel.loader.internal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

import java.nio.file.Path;

/** Mixin-backed access to Minecraft's dynamic level lifecycle. */
public interface MinecraftServerWorldBridge {
    ServerLevel aerogel$createLevel(
        ResourceKey<Level> levelKey, LevelStem stem, long seed
    );

    boolean aerogel$unloadLevel(ResourceKey<Level> levelKey);

    Path aerogel$worldDirectory();

    Path aerogel$dimensionDirectory(ResourceKey<Level> levelKey);
}
