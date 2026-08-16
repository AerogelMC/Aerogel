package dev.aerogel.api.persistence;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A plugin-namespaced persistent data view backed by vanilla save data. */
public interface PersistentDataContainer {
    void set(String key, byte value);
    void set(String key, short value);
    void set(String key, int value);
    void set(String key, long value);
    void set(String key, float value);
    void set(String key, double value);
    void set(String key, boolean value);
    void set(String key, String value);
    void set(String key, UUID value);
    void set(String key, byte[] value);
    void set(String key, int[] value);
    void set(String key, long[] value);
    void set(String key, CompoundTag value);

    Optional<Byte> getByte(String key);
    Optional<Short> getShort(String key);
    Optional<Integer> getInt(String key);
    Optional<Long> getLong(String key);
    Optional<Float> getFloat(String key);
    Optional<Double> getDouble(String key);
    Optional<Boolean> getBoolean(String key);
    Optional<String> getString(String key);
    Optional<UUID> getUUID(String key);
    Optional<byte[]> getByteArray(String key);
    Optional<int[]> getIntArray(String key);
    Optional<long[]> getLongArray(String key);
    Optional<CompoundTag> getCompound(String key);

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
