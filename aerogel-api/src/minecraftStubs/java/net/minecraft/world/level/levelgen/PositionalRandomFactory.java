package net.minecraft.world.level.levelgen;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface PositionalRandomFactory {
    RandomSource fromHashOf(Identifier identifier);
    RandomSource fromSeed(long seed);
}
