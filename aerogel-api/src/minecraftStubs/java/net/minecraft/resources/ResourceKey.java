package net.minecraft.resources;

import net.minecraft.core.Registry;

public class ResourceKey<T> {
    public Identifier identifier() { return null; }
    public static <T> ResourceKey<T> create(
        ResourceKey<? extends Registry<T>> registry, Identifier identifier
    ) {
        return null;
    }
}
