package net.minecraft.nbt;

import java.util.Optional;

public final class ByteArrayTag implements Tag {
    private final byte[] value;
    public ByteArrayTag(byte[] value) { this.value = value.clone(); }
    @Override public byte getId() { return TAG_BYTE_ARRAY; }
    @Override public Optional<byte[]> asByteArray() { return Optional.of(value.clone()); }
}
