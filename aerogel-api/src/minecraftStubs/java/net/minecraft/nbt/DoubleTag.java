package net.minecraft.nbt;

import java.util.Optional;

public final class DoubleTag implements Tag {
    private final double value;
    private DoubleTag(double value) { this.value = value; }
    public static DoubleTag valueOf(double value) { return new DoubleTag(value); }
    @Override public byte getId() { return TAG_DOUBLE; }
    @Override public Optional<Number> asNumber() { return Optional.of(value); }
}
