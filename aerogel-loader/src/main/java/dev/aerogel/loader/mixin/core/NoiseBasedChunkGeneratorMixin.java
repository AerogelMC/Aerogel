package dev.aerogel.loader.mixin.core;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Unique
    private int aerogel$seaLevel = Integer.MIN_VALUE;

    /**
     * Cache an immutable generator setting after its holder has been bound.
     *
     * @author Aerogel
     * @reason Avoid resolving the same holder in the per-block generation hot path.
     */
    @Overwrite
    public int getSeaLevel() {
        int seaLevel = this.aerogel$seaLevel;
        if (seaLevel == Integer.MIN_VALUE) {
            seaLevel = this.settings.value().seaLevel();
            this.aerogel$seaLevel = seaLevel;
        }
        return seaLevel;
    }
}
