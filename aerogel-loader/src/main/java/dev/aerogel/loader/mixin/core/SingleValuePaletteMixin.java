package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.PaletteCacheOwner;
import dev.aerogel.loader.worldgen.RawPaletteAccess;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SingleValuePalette.class)
abstract class SingleValuePaletteMixin<T> implements RawPaletteAccess<T> {
    @Shadow private T value;
    @Unique private Object[] aerogel$rawValues;

    @Override
    public Object[] aerogel$rawPalette(PaletteCacheOwner owner) {
        Object[] values = aerogel$rawValues;
        if (values == null) aerogel$rawValues = values = new Object[] {value};
        return values;
    }

    @Inject(method = "idFor", at = @At("RETURN"))
    private void aerogel$refreshAfterIdFor(CallbackInfoReturnable<Integer> callback) {
        aerogel$refreshRawValue();
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void aerogel$refreshAfterRead(CallbackInfo callback) {
        aerogel$refreshRawValue();
    }

    @Unique
    private void aerogel$refreshRawValue() {
        Object[] values = aerogel$rawValues;
        if (values != null) values[0] = value;
    }
}
