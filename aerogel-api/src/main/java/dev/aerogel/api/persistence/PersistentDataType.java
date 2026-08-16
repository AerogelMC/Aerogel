package dev.aerogel.api.persistence;

import net.minecraft.nbt.Tag;

/** Lossless conversion used by a persistent-data entry. */
public interface PersistentDataType<T> {
    Tag encode(T value);
    T decode(Tag tag);
}
