package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;

public interface ValueOutput {
    <T> void store(String key, Codec<T> codec, T value);
}
