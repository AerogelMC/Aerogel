package net.minecraft.nbt;

import java.util.Optional;

public final class LongTag implements Tag {
    private final long value;
    private LongTag(long value) { this.value = value; }
    public static LongTag valueOf(long value) { return new LongTag(value); }
    @Override public byte getId() { return TAG_LONG; }
    @Override public Optional<Number> asNumber() { return Optional.of(value); }
}
