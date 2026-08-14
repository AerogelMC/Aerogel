package net.minecraft.world.level.storage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

public final class LevelStorageSource {
    public static final class LevelDirectory {
        public Path path() { return null; }
    }

    public static final class LevelStorageAccess {
        public LevelDirectory getLevelDirectory() { return null; }
        public Path getDimensionPath(ResourceKey<Level> level) { return null; }
    }
}
