package net.minecraft.world.level.dimension;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;

public record LevelStem(Holder<DimensionType> type, ChunkGenerator generator) {
    public static final ResourceKey<LevelStem> OVERWORLD = null;
    public static final ResourceKey<LevelStem> NETHER = null;
    public static final ResourceKey<LevelStem> END = null;
}
