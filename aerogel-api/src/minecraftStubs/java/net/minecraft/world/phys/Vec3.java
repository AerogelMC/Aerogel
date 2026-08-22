package net.minecraft.world.phys;

public class Vec3 {
    public final double x;
    public final double y;
    public final double z;
    public Vec3() { this(0.0D, 0.0D, 0.0D); }
    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
