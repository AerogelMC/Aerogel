package net.minecraft.world.level.saveddata;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.function.Supplier;

public record SavedDataType<T extends SavedData>(
    Identifier id,
    Supplier<T> constructor,
    Codec<T> codec,
    DataFixTypes dataFixType
) { }
