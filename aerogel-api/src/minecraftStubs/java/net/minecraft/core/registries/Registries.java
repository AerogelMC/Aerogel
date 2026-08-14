package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class Registries {
    public static final ResourceKey<Registry<Level>> DIMENSION = null;
    public static final ResourceKey<Registry<LevelStem>> LEVEL_STEM = null;
    public static final ResourceKey<Registry<Biome>> BIOME = null;
    public static final ResourceKey<Registry<StructureSet>> STRUCTURE_SET = null;
    public static final ResourceKey<Registry<PlacedFeature>> PLACED_FEATURE = null;

    private Registries() {
    }
}
