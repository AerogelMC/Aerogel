package net.minecraft.core;

import com.mojang.serialization.Codec;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class BlockPos {
    public static final Codec<BlockPos> CODEC = null;
    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public BlockPos relative(Direction direction) {
        return switch (direction) {
            case DOWN -> new BlockPos(x, y - 1, z);
            case UP -> new BlockPos(x, y + 1, z);
            case NORTH -> new BlockPos(x, y, z - 1);
            case SOUTH -> new BlockPos(x, y, z + 1);
            case WEST -> new BlockPos(x - 1, y, z);
            case EAST -> new BlockPos(x + 1, y, z);
        };
    }
    public BlockPos immutable() { return this; }
    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
