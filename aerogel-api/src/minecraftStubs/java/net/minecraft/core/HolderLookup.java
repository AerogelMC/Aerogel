package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.RegistryOps;

public interface HolderLookup<T> {
    interface Provider {
        default <V> RegistryOps<V> createSerializationContext(DynamicOps<V> parent) {
            return null;
        }
    }
}
