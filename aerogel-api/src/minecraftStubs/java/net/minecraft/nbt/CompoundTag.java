package net.minecraft.nbt;

import com.mojang.serialization.Codec;

import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public final class CompoundTag implements Tag {
    public static final Codec<CompoundTag> CODEC = null;
    private final Map<String, Tag> values = new LinkedHashMap<>();

    public Set<Map.Entry<String, Tag>> entrySet() { return values.entrySet(); }
    public boolean contains(String key) { return values.containsKey(key); }
    public Tag get(String key) { return values.get(key); }
    public Tag put(String key, Tag value) { return values.put(key, value); }
    public Tag remove(String key) { return values.remove(key); }
    public Set<String> keySet() { return values.keySet(); }
    public boolean isEmpty() { return values.isEmpty(); }
    public CompoundTag getCompoundOrEmpty(String key) {
        Tag value = values.get(key);
        return value instanceof CompoundTag compound ? compound : new CompoundTag();
    }
    public CompoundTag copy() {
        CompoundTag result = new CompoundTag();
        values.forEach((key, value) -> result.put(key, value.copy()));
        return result;
    }
    @Override public byte getId() { return TAG_COMPOUND; }
    @Override public Optional<CompoundTag> asCompound() { return Optional.of(this); }
}
