package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;

import java.util.Optional;

public interface ValueInput {
    <T> Optional<T> read(String key, Codec<T> codec);
}
