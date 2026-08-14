package net.minecraft.nbt;

import java.util.Optional;

public final class FloatTag implements Tag {
    private final float value;
    private FloatTag(float value) { this.value = value; }
    public static FloatTag valueOf(float value) { return new FloatTag(value); }
    @Override public byte getId() { return TAG_FLOAT; }
    @Override public Optional<Number> asNumber() { return Optional.of(value); }
}
