package dev.aerogel.loader.mixin.core;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.ChunkAccess")
abstract class ChunkAccessBiomeMixin {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Shadow @Final protected LevelChunkSection[] sections;
    @Unique private int aerogel$minSectionY;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$cacheMinSection(CallbackInfo callback) {
        aerogel$minSectionY = levelHeightAccessor.getMinSectionY();
    }

    /**
     * @author Spottedleaf, Aerogel
     * @reason Use cached section bounds and direct power-of-two coordinate conversion.
     */
    @Overwrite
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        try {
            int sectionY = (quartY >> 2) - aerogel$minSectionY;
            int relativeY = quartY & 3;
            if (sectionY < 0) {
                sectionY = 0;
                relativeY = 0;
            } else if (sectionY >= sections.length) {
                sectionY = sections.length - 1;
                relativeY = 3;
            }
            return sections[sectionY].getNoiseBiome(quartX & 3, relativeY, quartZ & 3);
        } catch (Throwable throwable) {
            CrashReport report = CrashReport.forThrowable(throwable, "Getting biome");
            CrashReportCategory category = report.addCategory("Biome being got");
            category.setDetail("Location", () -> CrashReportCategory.formatLocation(
                (LevelHeightAccessor) (Object) this, quartX, quartY, quartZ));
            throw new ReportedException(report);
        }
    }
}
