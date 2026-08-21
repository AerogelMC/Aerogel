package dev.aerogel.loader.context;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import java.util.Objects;

/**
 * World random facade that selects the current Context owner's RNG for every access,
 * including bytecode that reads Level.random directly instead of calling getRandom().
 */
public final class ContextDispatchingRandomSource implements RandomSource {
    private final Level level;
    private final RandomSource fallback;

    public ContextDispatchingRandomSource(Level level, RandomSource fallback) {
        this.level = Objects.requireNonNull(level, "level");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    private RandomSource delegate() {
        RandomSource owned = ContextRandomRouting.current(level);
        return owned != null ? owned : fallback;
    }

    @Override public RandomSource fork() { return delegate().fork(); }
    @Override public PositionalRandomFactory forkPositional() {
        return delegate().forkPositional();
    }
    @Override public void setSeed(long seed) { delegate().setSeed(seed); }
    @Override public int nextInt() { return delegate().nextInt(); }
    @Override public int nextInt(int bound) { return delegate().nextInt(bound); }
    @Override public long nextLong() { return delegate().nextLong(); }
    @Override public boolean nextBoolean() { return delegate().nextBoolean(); }
    @Override public float nextFloat() { return delegate().nextFloat(); }
    @Override public double nextDouble() { return delegate().nextDouble(); }
    @Override public double nextGaussian() { return delegate().nextGaussian(); }
}
