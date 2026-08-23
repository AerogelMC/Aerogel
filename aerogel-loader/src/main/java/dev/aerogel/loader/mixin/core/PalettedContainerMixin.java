package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.worldgen.PaletteCacheOwner;
import dev.aerogel.loader.worldgen.RawPaletteAccess;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.chunk.PalettedContainer")
abstract class PalettedContainerMixin<T> implements PaletteCacheOwner {
    @Unique private Palette<T> aerogel$cachedPalette;
    @Unique private volatile Object[] aerogel$cachedValues;

    /**
     * Chunk generation and simulation mutate containers through their owning context.
     *
     * @author Aerogel
     * @reason The vanilla semaphore duplicates context ownership and dominates block writes.
     */
    @Overwrite
    public void acquire() {
    }

    /**
     * @author Aerogel
     * @reason Paired no-op for the ownership-enforced acquire path.
     */
    @Overwrite
    public void release() {
    }

    @Redirect(
        method = {
            "get(I)Ljava/lang/Object;",
            "getAndSet(ILjava/lang/Object;)Ljava/lang/Object;"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/Palette;valueFor(I)Ljava/lang/Object;"
        )
    )
    @SuppressWarnings("unchecked")
    private T aerogel$readPaletteArray(Palette<T> palette, int id) {
        Object[] values = aerogel$cachedValues;
        if (palette != aerogel$cachedPalette) {
            aerogel$cachedPalette = palette;
            values = palette instanceof RawPaletteAccess<?> access
                ? access.aerogel$rawPalette(this)
                : null;
            aerogel$cachedValues = values;
        }
        if (values != null && id >= 0 && id < values.length) {
            Object value = values[id];
            if (value != null) return (T) value;
        }
        return palette.valueFor(id);
    }

    @Override
    public void aerogel$replaceRawPalette(Object[] values) {
        aerogel$cachedValues = values;
    }
}
