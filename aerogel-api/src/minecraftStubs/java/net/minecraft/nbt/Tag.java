package net.minecraft.nbt;

import java.util.Optional;

public interface Tag {
    byte TAG_END = 0;
    byte TAG_BYTE = 1;
    byte TAG_SHORT = 2;
    byte TAG_INT = 3;
    byte TAG_LONG = 4;
    byte TAG_FLOAT = 5;
    byte TAG_DOUBLE = 6;
    byte TAG_BYTE_ARRAY = 7;
    byte TAG_STRING = 8;
    byte TAG_LIST = 9;
    byte TAG_COMPOUND = 10;
    byte TAG_INT_ARRAY = 11;
    byte TAG_LONG_ARRAY = 12;

    byte getId();
    default Optional<String> asString() { return Optional.empty(); }
    default Optional<Number> asNumber() { return Optional.empty(); }
    default Optional<byte[]> asByteArray() { return Optional.empty(); }
    default Optional<int[]> asIntArray() { return Optional.empty(); }
    default Optional<long[]> asLongArray() { return Optional.empty(); }
    default Optional<CompoundTag> asCompound() { return Optional.empty(); }
    default Optional<ListTag> asList() { return Optional.empty(); }
    default Tag copy() { return this; }
}
