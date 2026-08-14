package net.minecraft.world.level.levelgen.flat;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;
import java.util.Optional;

/** Compile-only Minecraft stub. The official server class is used at runtime and in plugin development. */
public class FlatLevelGeneratorSettings {
    public FlatLevelGeneratorSettings(
        Optional<HolderSet<StructureSet>> structures,
        Holder<Biome> biome,
        List<Holder<PlacedFeature>> lakes
    ) {
    }

    public static FlatLevelGeneratorSettings getDefault(
        HolderGetter<Biome> biomes,
        HolderGetter<StructureSet> structures,
        HolderGetter<PlacedFeature> placedFeatures
    ) {
        return null;
    }

    public void updateLayers() {
    }
}
