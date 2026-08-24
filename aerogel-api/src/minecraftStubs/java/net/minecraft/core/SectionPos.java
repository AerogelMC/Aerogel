package net.minecraft.core;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public final class SectionPos {
    public static SectionPos of(long packed) { return null; }
    public static SectionPos of(BlockPos position) { return null; }
    public static SectionPos of(EntityAccess entity) { return null; }
    public static int x(long packed) { return (int) (packed >> 42); }
    public static int y(long packed) { return (int) (packed << 44 >> 44); }
    public static int z(long packed) { return (int) (packed << 22 >> 42); }
    public static long offset(long packed, int x, int y, int z) {
        return asLong(x(packed) + x, y(packed) + y, z(packed) + z);
    }
    public static long asLong(int x, int y, int z) {
        return ((long) x & 0x3fffffL) << 42
            | ((long) z & 0x3fffffL) << 20
            | (long) y & 0xfffffL;
    }
    public static long asLong(BlockPos position) {
        return asLong(position.getX() >> 4, position.getY() >> 4, position.getZ() >> 4);
    }
    public static short sectionRelativePos(BlockPos position) { return 0; }
    public long asLong() { return 0L; }
    public int x() { return 0; }
    public int y() { return 0; }
    public int z() { return 0; }
    public ChunkPos chunk() { return null; }
}
