package dev.aerogel.loader.worldgen;

/** Receives a replacement palette array when a palette grows in place. */
public interface PaletteCacheOwner {
    void aerogel$replaceRawPalette(Object[] values);
}
