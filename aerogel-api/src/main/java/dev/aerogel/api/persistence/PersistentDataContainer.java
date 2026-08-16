package dev.aerogel.api.persistence;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A plugin-namespaced persistent data view backed by vanilla save data. */
public interface PersistentDataContainer {
    <T> void set(String key, PersistentDataType<T> type, T value);
    <T> Optional<T> get(String key, PersistentDataType<T> type);

    default void set(String key, byte value) { set(key, PersistentDataTypes.BYTE, value); }
    default void set(String key, short value) { set(key, PersistentDataTypes.SHORT, value); }
    default void set(String key, int value) { set(key, PersistentDataTypes.INTEGER, value); }
    default void set(String key, long value) { set(key, PersistentDataTypes.LONG, value); }
    default void set(String key, float value) { set(key, PersistentDataTypes.FLOAT, value); }
    default void set(String key, double value) { set(key, PersistentDataTypes.DOUBLE, value); }
    default void set(String key, boolean value) { set(key, PersistentDataTypes.BOOLEAN, value); }
    default void set(String key, String value) { set(key, PersistentDataTypes.STRING, value); }
    default void set(String key, UUID value) { set(key, PersistentDataTypes.UUID, value); }
    default void set(String key, byte[] value) { set(key, PersistentDataTypes.BYTE_ARRAY, value); }
    default void set(String key, int[] value) { set(key, PersistentDataTypes.INTEGER_ARRAY, value); }
    default void set(String key, long[] value) { set(key, PersistentDataTypes.LONG_ARRAY, value); }
    default void set(String key, CompoundTag value) { set(key, PersistentDataTypes.COMPOUND, value); }

    default Optional<Byte> getByte(String key) { return get(key, PersistentDataTypes.BYTE); }
    default Optional<Short> getShort(String key) { return get(key, PersistentDataTypes.SHORT); }
    default Optional<Integer> getInt(String key) { return get(key, PersistentDataTypes.INTEGER); }
    default Optional<Long> getLong(String key) { return get(key, PersistentDataTypes.LONG); }
    default Optional<Float> getFloat(String key) { return get(key, PersistentDataTypes.FLOAT); }
    default Optional<Double> getDouble(String key) { return get(key, PersistentDataTypes.DOUBLE); }
    default Optional<Boolean> getBoolean(String key) { return get(key, PersistentDataTypes.BOOLEAN); }
    default Optional<String> getString(String key) { return get(key, PersistentDataTypes.STRING); }
    default Optional<UUID> getUUID(String key) { return get(key, PersistentDataTypes.UUID); }
    default Optional<byte[]> getByteArray(String key) { return get(key, PersistentDataTypes.BYTE_ARRAY); }
    default Optional<int[]> getIntArray(String key) { return get(key, PersistentDataTypes.INTEGER_ARRAY); }
    default Optional<long[]> getLongArray(String key) { return get(key, PersistentDataTypes.LONG_ARRAY); }
    default Optional<CompoundTag> getCompound(String key) { return get(key, PersistentDataTypes.COMPOUND); }

    default byte getByte(String key, byte fallback) { return getByte(key).orElse(fallback); }
    default short getShort(String key, short fallback) { return getShort(key).orElse(fallback); }
    default int getInt(String key, int fallback) { return getInt(key).orElse(fallback); }
    default long getLong(String key, long fallback) { return getLong(key).orElse(fallback); }
    default float getFloat(String key, float fallback) { return getFloat(key).orElse(fallback); }
    default double getDouble(String key, double fallback) { return getDouble(key).orElse(fallback); }
    default boolean getBoolean(String key, boolean fallback) { return getBoolean(key).orElse(fallback); }
    default String getString(String key, String fallback) { return getString(key).orElse(fallback); }
    default UUID getUUID(String key, UUID fallback) { return getUUID(key).orElse(fallback); }

    boolean contains(String key);
    void remove(String key);
    Set<String> keys();
    void clear();
    CompoundTag snapshot();
}
