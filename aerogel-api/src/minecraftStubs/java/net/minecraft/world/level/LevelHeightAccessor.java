package net.minecraft.world.level;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface LevelHeightAccessor {
    int getHeight();
    int getMinY();
    default int getMinSectionY() { return getMinY() >> 4; }
}
