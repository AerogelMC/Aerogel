package net.minecraft.nbt;

import java.util.Optional;

public final class ShortTag implements Tag {
    private final short value;
    private ShortTag(short value) { this.value = value; }
    public static ShortTag valueOf(short value) { return new ShortTag(value); }
    @Override public byte getId() { return TAG_SHORT; }
    @Override public Optional<Number> asNumber() { return Optional.of(value); }
}
