package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

public interface Registry<T> extends HolderLookup.RegistryLookup<T> {
    T getValue(ResourceKey<T> key);
}
