package net.minecraft.nbt;

import java.util.Optional;

public final class ByteTag implements Tag {
    private final byte value;
    private ByteTag(byte value) { this.value = value; }
    public static ByteTag valueOf(byte value) { return new ByteTag(value); }
    public static ByteTag valueOf(boolean value) { return new ByteTag((byte) (value ? 1 : 0)); }
    @Override public byte getId() { return TAG_BYTE; }
    @Override public Optional<Number> asNumber() { return Optional.of(value); }
}
