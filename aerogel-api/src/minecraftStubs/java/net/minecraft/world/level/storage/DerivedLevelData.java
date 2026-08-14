package net.minecraft.world.level.storage;

public final class DerivedLevelData implements ServerLevelData {
    public DerivedLevelData(WorldData worldData, ServerLevelData wrapped) {
    }

    @Override public RespawnData getRespawnData() { return null; }
}
