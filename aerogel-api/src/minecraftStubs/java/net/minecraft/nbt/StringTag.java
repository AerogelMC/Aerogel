package net.minecraft.nbt;

import java.util.Optional;

public final class StringTag implements Tag {
    private final String value;
    private StringTag(String value) { this.value = value; }
    public static StringTag valueOf(String value) { return new StringTag(value); }
    @Override public byte getId() { return TAG_STRING; }
    @Override public Optional<String> asString() { return Optional.of(value); }
}
