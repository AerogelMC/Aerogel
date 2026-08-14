package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

public interface HolderGetter<T> {
    default Holder.Reference<T> getOrThrow(ResourceKey<T> key) { return null; }
}
