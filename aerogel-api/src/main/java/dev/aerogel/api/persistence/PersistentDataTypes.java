package dev.aerogel.api.persistence;

import net.minecraft.nbt.*;

import java.util.Objects;
import java.util.UUID;

/** Built-in lossless persistent-data types. */
public final class PersistentDataTypes {
    public static final PersistentDataType<Byte> BYTE = number(ByteTag.TAG_BYTE, Number::byteValue, ByteTag::valueOf);
    public static final PersistentDataType<Short> SHORT = number(Tag.TAG_SHORT, Number::shortValue, ShortTag::valueOf);
    public static final PersistentDataType<Integer> INTEGER = number(Tag.TAG_INT, Number::intValue, IntTag::valueOf);
    public static final PersistentDataType<Long> LONG = number(Tag.TAG_LONG, Number::longValue, LongTag::valueOf);
    public static final PersistentDataType<Float> FLOAT = number(Tag.TAG_FLOAT, Number::floatValue, FloatTag::valueOf);
    public static final PersistentDataType<Double> DOUBLE = number(Tag.TAG_DOUBLE, Number::doubleValue, DoubleTag::valueOf);
    public static final PersistentDataType<Boolean> BOOLEAN = new PersistentDataType<>() {
        @Override public Tag encode(Boolean value) { return ByteTag.valueOf(Objects.requireNonNull(value)); }
        @Override public Boolean decode(Tag tag) { return numberValue(tag, Tag.TAG_BYTE).byteValue() != 0; }
    };
    public static final PersistentDataType<String> STRING = new PersistentDataType<>() {
        @Override public Tag encode(String value) { return StringTag.valueOf(Objects.requireNonNull(value)); }
        @Override public String decode(Tag tag) { return tag.asString().orElseThrow(() -> mismatch(Tag.TAG_STRING, tag)); }
    };
    public static final PersistentDataType<byte[]> BYTE_ARRAY = new PersistentDataType<>() {
        @Override public Tag encode(byte[] value) { return new ByteArrayTag(Objects.requireNonNull(value)); }
        @Override public byte[] decode(Tag tag) { return tag.asByteArray().orElseThrow(() -> mismatch(Tag.TAG_BYTE_ARRAY, tag)); }
    };
    public static final PersistentDataType<int[]> INTEGER_ARRAY = new PersistentDataType<>() {
        @Override public Tag encode(int[] value) { return new IntArrayTag(Objects.requireNonNull(value)); }
        @Override public int[] decode(Tag tag) { return tag.asIntArray().orElseThrow(() -> mismatch(Tag.TAG_INT_ARRAY, tag)); }
    };
    public static final PersistentDataType<long[]> LONG_ARRAY = new PersistentDataType<>() {
        @Override public Tag encode(long[] value) { return new LongArrayTag(Objects.requireNonNull(value)); }
        @Override public long[] decode(Tag tag) { return tag.asLongArray().orElseThrow(() -> mismatch(Tag.TAG_LONG_ARRAY, tag)); }
    };
    public static final PersistentDataType<UUID> UUID = new PersistentDataType<>() {
        @Override public Tag encode(UUID value) {
            Objects.requireNonNull(value);
            return new IntArrayTag(new int[] {(int) (value.getMostSignificantBits() >> 32),
                (int) value.getMostSignificantBits(), (int) (value.getLeastSignificantBits() >> 32),
                (int) value.getLeastSignificantBits()});
        }
        @Override public UUID decode(Tag tag) {
            int[] values = INTEGER_ARRAY.decode(tag);
            if (values.length != 4) throw new IllegalArgumentException("Persistent UUID must contain four integers");
            return new UUID(((long) values[0] << 32) | (values[1] & 0xffffffffL),
                ((long) values[2] << 32) | (values[3] & 0xffffffffL));
        }
    };
    public static final PersistentDataType<CompoundTag> COMPOUND = new PersistentDataType<>() {
        @Override public Tag encode(CompoundTag value) { return Objects.requireNonNull(value).copy(); }
        @Override public CompoundTag decode(Tag tag) {
            return tag.asCompound().orElseThrow(() -> mismatch(Tag.TAG_COMPOUND, tag)).copy();
        }
    };

    private PersistentDataTypes() { }

    private static <T> PersistentDataType<T> number(
        byte expected, java.util.function.Function<Number, T> decoder,
        java.util.function.Function<T, Tag> encoder
    ) {
        return new PersistentDataType<>() {
            @Override public Tag encode(T value) { return encoder.apply(Objects.requireNonNull(value)); }
            @Override public T decode(Tag tag) { return decoder.apply(numberValue(tag, expected)); }
        };
    }

    private static Number numberValue(Tag tag, byte expected) {
        if (tag.getId() != expected) throw mismatch(expected, tag);
        return tag.asNumber().orElseThrow(() -> mismatch(expected, tag));
    }

    private static IllegalArgumentException mismatch(byte expected, Tag actual) {
        return new IllegalArgumentException("Persistent data tag type mismatch: expected " + expected
            + ", got " + actual.getId());
    }
}
