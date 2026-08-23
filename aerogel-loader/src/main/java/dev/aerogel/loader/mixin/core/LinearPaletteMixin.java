package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.RawPaletteAccess;
import dev.aerogel.loader.worldgen.PaletteCacheOwner;
import net.minecraft.world.level.chunk.LinearPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LinearPalette.class)
abstract class LinearPaletteMixin<T> implements RawPaletteAccess<T> {
    @Shadow @Final private T[] values;

    @Override
    public Object[] aerogel$rawPalette(PaletteCacheOwner owner) {
        return values;
    }
}
