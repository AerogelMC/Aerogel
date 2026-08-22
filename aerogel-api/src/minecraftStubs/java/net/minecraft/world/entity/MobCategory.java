package net.minecraft.world.entity;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public enum MobCategory {
    MONSTER(70);

    private final int maxInstancesPerChunk;

    MobCategory(int maxInstancesPerChunk) {
        this.maxInstancesPerChunk = maxInstancesPerChunk;
    }

    public int getMaxInstancesPerChunk() { return maxInstancesPerChunk; }
}
