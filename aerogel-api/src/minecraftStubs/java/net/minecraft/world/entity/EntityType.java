package net.minecraft.world.entity;

public class EntityType<T extends Entity> {
    public MobCategory getCategory() { return null; }
    public int updateInterval() { return 0; }
    public boolean trackDeltas() { return false; }
}
