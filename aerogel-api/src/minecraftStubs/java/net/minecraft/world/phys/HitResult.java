package net.minecraft.world.phys;
public abstract class HitResult {
    public abstract Type getType();

    public enum Type {
        MISS,
        BLOCK,
        ENTITY
    }
}
