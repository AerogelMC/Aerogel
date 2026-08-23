package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.RawPaletteAccess;
import dev.aerogel.loader.worldgen.PaletteCacheOwner;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrudeIncrementalIntIdentityHashBiMap.class)
abstract class CrudeIdentityPaletteMixin<T> implements RawPaletteAccess<T> {
    @Shadow private T[] byId;
    @Unique private PaletteCacheOwner aerogel$cacheOwner;

    @Override
    public Object[] aerogel$rawPalette(PaletteCacheOwner owner) {
        aerogel$cacheOwner = owner;
        return byId;
    }

    @Inject(method = "grow(I)V", at = @At("RETURN"))
    private void aerogel$publishGrownPalette(int capacity, CallbackInfo callback) {
        PaletteCacheOwner owner = aerogel$cacheOwner;
        if (owner != null) owner.aerogel$replaceRawPalette(byId);
    }
}
