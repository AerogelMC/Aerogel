package net.minecraft.world.level;

/** Compile-time surface matching Minecraft 26.2's ChunkPos record. */
public record ChunkPos(int x, int z) {
    public long pack() { return pack(x, z); }

    public static long pack(int x, int z) {
        return (x & 0xffffffffL) | ((long) z & 0xffffffffL) << 32;
    }

    public static int getX(long packed) {
        return (int) packed;
    }

    public static int getZ(long packed) {
        return (int) (packed >>> 32);
    }
}
