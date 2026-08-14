package net.minecraft.nbt;

import java.util.Optional;

public final class LongArrayTag implements Tag {
    private final long[] value;
    public LongArrayTag(long[] value) { this.value = value.clone(); }
    @Override public byte getId() { return TAG_LONG_ARRAY; }
    @Override public Optional<long[]> asLongArray() { return Optional.of(value.clone()); }
}
