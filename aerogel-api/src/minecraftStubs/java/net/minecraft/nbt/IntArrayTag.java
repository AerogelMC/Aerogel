package net.minecraft.nbt;

import java.util.Optional;

public final class IntArrayTag implements Tag {
    private final int[] value;
    public IntArrayTag(int[] value) { this.value = value.clone(); }
    @Override public byte getId() { return TAG_INT_ARRAY; }
    @Override public Optional<int[]> asIntArray() { return Optional.of(value.clone()); }
}
