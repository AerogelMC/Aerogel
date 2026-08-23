package dev.aerogel.loader.mixin.core;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BiomeManager.class)
abstract class BiomeManagerMixin {
    /**
     * Power-of-two remainder and constant multiplication, bit-identical for all inputs.
     *
     * @author Spottedleaf, Aerogel
     * @reason Avoid floorMod and floating-point division in the biome lookup hot path.
     */
    @Overwrite
    private static double getFiddle(long value) {
        return (double) (((value >> 24) & 1023L) - 512L) * (0.9 / 1024.0);
    }
}
