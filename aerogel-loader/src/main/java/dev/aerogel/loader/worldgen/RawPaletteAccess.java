package dev.aerogel.loader.worldgen;

/** Mixin bridge exposing a palette's existing id-indexed backing array. */
public interface RawPaletteAccess<T> {
    Object[] aerogel$rawPalette(PaletteCacheOwner owner);
}
