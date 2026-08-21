package net.minecraft.util;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface RandomSource {
    static RandomSource create(long seed) { return null; }
    RandomSource fork();
    net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional();
    void setSeed(long seed);
    int nextInt();
    int nextInt(int bound);
    long nextLong();
    boolean nextBoolean();
    float nextFloat();
    double nextDouble();
    double nextGaussian();
}
