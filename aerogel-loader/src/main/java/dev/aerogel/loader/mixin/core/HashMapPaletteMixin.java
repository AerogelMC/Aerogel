package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.RawPaletteAccess;
import dev.aerogel.loader.worldgen.PaletteCacheOwner;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HashMapPalette.class)
abstract class HashMapPaletteMixin<T> implements RawPaletteAccess<T> {
    @Shadow @Final private CrudeIncrementalIntIdentityHashBiMap<T> values;

    @Override
    public Object[] aerogel$rawPalette(PaletteCacheOwner owner) {
        return ((RawPaletteAccess<?>) values).aerogel$rawPalette(owner);
    }
}
