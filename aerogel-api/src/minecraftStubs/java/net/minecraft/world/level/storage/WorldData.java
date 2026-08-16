package net.minecraft.world.level.storage;

public interface WorldData {
    default net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return null; }
    ServerLevelData overworldData();
    boolean isDebugWorld();
}
