package net.minecraft.nbt;

import java.util.Optional;

public final class IntTag implements Tag {
    private final int value;
    private IntTag(int value) { this.value = value; }
    public static IntTag valueOf(int value) { return new IntTag(value); }
    @Override public byte getId() { return TAG_INT; }
    @Override public Optional<Number> asNumber() { return Optional.of(value); }
}
