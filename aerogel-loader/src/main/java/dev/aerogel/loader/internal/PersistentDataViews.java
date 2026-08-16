package dev.aerogel.loader.internal;

import dev.aerogel.api.persistence.PersistentDataContainer;
import dev.aerogel.api.persistence.PersistentDataView;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Creates direct persistent-data views for vanilla objects extended by Aerogel. */
public final class PersistentDataViews {
    private static final String ITEM_ROOT = "aerogel";

    private PersistentDataViews() { }

    public static PersistentDataView entity(PersistentDataHolderBridge holder) {
        return holder(holder);
    }

    public static PersistentDataView holder(PersistentDataHolderBridge holder) {
        return new EntityView(Objects.requireNonNull(holder, "holder"));
    }

    public static PersistentDataView item(ItemStack stack) {
        return new ItemView(Objects.requireNonNull(stack, "stack"));
    }

    public static PersistentDataView server(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return saved(server.overworld(), "server");
    }

    public static PersistentDataView world(ServerLevel level) {
        return saved(Objects.requireNonNull(level, "level"), "world");
    }

    public static PersistentDataView block(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(position, "position");
        return saved(Objects.requireNonNull(level, "level"), "blocks/" + position.getX()
            + "/" + position.getY() + "/" + position.getZ());
    }

    private static PersistentDataView saved(ServerLevel level, String target) {
        return new SavedView(level, target);
    }

    private static final class EntityView implements PersistentDataView {
        private final PersistentDataHolderBridge holder;

        private EntityView(PersistentDataHolderBridge holder) {
            this.holder = holder;
        }

        @Override
        public PersistentDataContainer namespace(String namespace) {
            validateNamespace(namespace);
            return new EntityContainer(holder, namespace);
        }

        @Override
        public Set<String> namespaces() {
            return Set.copyOf(holder.aerogel$allPersistentData().keySet());
        }

        @Override
        public boolean containsNamespace(String namespace) {
            validateNamespace(namespace);
            return holder.aerogel$allPersistentData().contains(namespace);
        }

        @Override
        public void removeNamespace(String namespace) {
            namespace(namespace).clear();
        }

        @Override
        public CompoundTag snapshot() {
            return holder.aerogel$allPersistentData();
        }
    }

    private static final class ItemView implements PersistentDataView {
        private final ItemStack stack;

        private ItemView(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public PersistentDataContainer namespace(String namespace) {
            validateNamespace(namespace);
            return new ItemContainer(stack, namespace);
        }

        @Override public Set<String> namespaces() { return Set.copyOf(root().keySet()); }
        @Override public boolean containsNamespace(String namespace) {
            validateNamespace(namespace);
            return root().contains(namespace);
        }
        @Override public void removeNamespace(String namespace) { namespace(namespace).clear(); }
        @Override public CompoundTag snapshot() { return root(); }

        private CompoundTag root() {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return data == null ? new CompoundTag()
                : data.copyTag().getCompoundOrEmpty(ITEM_ROOT).copy();
        }
    }

    private static final class SavedView implements PersistentDataView {
        private final ServerLevel level;
        private final String target;

        private SavedView(ServerLevel level, String target) {
            this.level = level;
            this.target = target;
        }

        @Override
        public PersistentDataContainer namespace(String namespace) {
            validateNamespace(namespace);
            return new SavedContainer(level, namespace, target);
        }

        @Override public Set<String> namespaces() { return data().namespaces(target); }
        @Override public boolean containsNamespace(String namespace) {
            validateNamespace(namespace);
            return data().contains(namespace, target);
        }
        @Override public void removeNamespace(String namespace) { namespace(namespace).clear(); }
        @Override public CompoundTag snapshot() { return data().snapshotTarget(target); }

        private AerogelPersistentSavedData data() {
            requireServerThread(level);
            return level.getDataStorage().computeIfAbsent(AerogelPersistentSavedData.TYPE);
        }
    }

    private static final class EntityContainer extends TypedContainer {
        private final PersistentDataHolderBridge holder;
        private final String namespace;

        private EntityContainer(PersistentDataHolderBridge holder, String namespace) {
            this.holder = holder;
            this.namespace = namespace;
        }

        @Override protected CompoundTag read() {
            return holder.aerogel$persistentData(namespace);
        }

        @Override protected void edit(Consumer<CompoundTag> editor) {
            holder.aerogel$editPersistentData(namespace, editor);
        }
    }

    private static final class ItemContainer extends TypedContainer {
        private final ItemStack stack;
        private final String namespace;

        private ItemContainer(ItemStack stack, String namespace) {
            this.stack = stack;
            this.namespace = namespace;
        }

        @Override protected CompoundTag read() {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return new CompoundTag();
            return data.copyTag().getCompoundOrEmpty(ITEM_ROOT)
                .getCompoundOrEmpty(namespace).copy();
        }

        @Override protected void edit(Consumer<CompoundTag> editor) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
                CompoundTag aerogel = root.getCompoundOrEmpty(ITEM_ROOT).copy();
                CompoundTag value = aerogel.getCompoundOrEmpty(namespace).copy();
                editor.accept(value);
                if (value.isEmpty()) aerogel.remove(namespace); else aerogel.put(namespace, value);
                if (aerogel.isEmpty()) root.remove(ITEM_ROOT); else root.put(ITEM_ROOT, aerogel);
            });
        }
    }

    private static final class SavedContainer extends TypedContainer {
        private final ServerLevel level;
        private final String namespace;
        private final String target;

        private SavedContainer(ServerLevel level, String namespace, String target) {
            this.level = level;
            this.namespace = namespace;
            this.target = target;
        }

        @Override protected CompoundTag read() { return data().snapshot(namespace, target); }
        @Override protected void edit(Consumer<CompoundTag> editor) {
            data().edit(namespace, target, editor);
        }

        private AerogelPersistentSavedData data() {
            requireServerThread(level);
            return level.getDataStorage().computeIfAbsent(AerogelPersistentSavedData.TYPE);
        }
    }

    private abstract static class TypedContainer implements PersistentDataContainer {
        protected abstract CompoundTag read();
        protected abstract void edit(Consumer<CompoundTag> editor);

        @Override public void set(String key, byte value) { put(key, ByteTag.valueOf(value)); }
        @Override public void set(String key, short value) { put(key, ShortTag.valueOf(value)); }
        @Override public void set(String key, int value) { put(key, IntTag.valueOf(value)); }
        @Override public void set(String key, long value) { put(key, LongTag.valueOf(value)); }
        @Override public void set(String key, float value) { put(key, FloatTag.valueOf(value)); }
        @Override public void set(String key, double value) { put(key, DoubleTag.valueOf(value)); }
        @Override public void set(String key, boolean value) { put(key, ByteTag.valueOf(value)); }
        @Override public void set(String key, String value) {
            put(key, StringTag.valueOf(Objects.requireNonNull(value, "value")));
        }
        @Override public void set(String key, UUID value) {
            Objects.requireNonNull(value, "value");
            put(key, new IntArrayTag(new int[] {
                (int) (value.getMostSignificantBits() >> 32),
                (int) value.getMostSignificantBits(),
                (int) (value.getLeastSignificantBits() >> 32),
                (int) value.getLeastSignificantBits()
            }));
        }
        @Override public void set(String key, byte[] value) {
            put(key, new ByteArrayTag(Objects.requireNonNull(value, "value")));
        }
        @Override public void set(String key, int[] value) {
            put(key, new IntArrayTag(Objects.requireNonNull(value, "value")));
        }
        @Override public void set(String key, long[] value) {
            put(key, new LongArrayTag(Objects.requireNonNull(value, "value")));
        }
        @Override public void set(String key, CompoundTag value) {
            put(key, Objects.requireNonNull(value, "value").copy());
        }

        @Override public Optional<Byte> getByte(String key) {
            return decode(key, Tag.TAG_BYTE, tag -> number(tag).byteValue());
        }
        @Override public Optional<Short> getShort(String key) {
            return decode(key, Tag.TAG_SHORT, tag -> number(tag).shortValue());
        }
        @Override public Optional<Integer> getInt(String key) {
            return decode(key, Tag.TAG_INT, tag -> number(tag).intValue());
        }
        @Override public Optional<Long> getLong(String key) {
            return decode(key, Tag.TAG_LONG, tag -> number(tag).longValue());
        }
        @Override public Optional<Float> getFloat(String key) {
            return decode(key, Tag.TAG_FLOAT, tag -> number(tag).floatValue());
        }
        @Override public Optional<Double> getDouble(String key) {
            return decode(key, Tag.TAG_DOUBLE, tag -> number(tag).doubleValue());
        }
        @Override public Optional<Boolean> getBoolean(String key) {
            return decode(key, Tag.TAG_BYTE, tag -> number(tag).byteValue() != 0);
        }
        @Override public Optional<String> getString(String key) {
            return decode(key, Tag.TAG_STRING, tag -> tag.asString()
                .orElseThrow(() -> mismatch(Tag.TAG_STRING, tag)));
        }
        @Override public Optional<UUID> getUUID(String key) {
            return decode(key, Tag.TAG_INT_ARRAY, tag -> {
                int[] values = tag.asIntArray()
                    .orElseThrow(() -> mismatch(Tag.TAG_INT_ARRAY, tag));
                if (values.length != 4) {
                    throw new IllegalArgumentException(
                        "Persistent UUID must contain four integers");
                }
                return new UUID(((long) values[0] << 32) | (values[1] & 0xffffffffL),
                    ((long) values[2] << 32) | (values[3] & 0xffffffffL));
            });
        }
        @Override public Optional<byte[]> getByteArray(String key) {
            return decode(key, Tag.TAG_BYTE_ARRAY, tag -> tag.asByteArray()
                .orElseThrow(() -> mismatch(Tag.TAG_BYTE_ARRAY, tag)).clone());
        }
        @Override public Optional<int[]> getIntArray(String key) {
            return decode(key, Tag.TAG_INT_ARRAY, tag -> tag.asIntArray()
                .orElseThrow(() -> mismatch(Tag.TAG_INT_ARRAY, tag)).clone());
        }
        @Override public Optional<long[]> getLongArray(String key) {
            return decode(key, Tag.TAG_LONG_ARRAY, tag -> tag.asLongArray()
                .orElseThrow(() -> mismatch(Tag.TAG_LONG_ARRAY, tag)).clone());
        }
        @Override public Optional<CompoundTag> getCompound(String key) {
            return decode(key, Tag.TAG_COMPOUND, tag -> tag.asCompound()
                .orElseThrow(() -> mismatch(Tag.TAG_COMPOUND, tag)).copy());
        }

        @Override public boolean contains(String key) { validateKey(key); return read().contains(key); }
        @Override public void remove(String key) { validateKey(key); edit(tag -> tag.remove(key)); }
        @Override public Set<String> keys() { return Set.copyOf(read().keySet()); }
        @Override public void clear() { edit(tag -> Set.copyOf(tag.keySet()).forEach(tag::remove)); }
        @Override public CompoundTag snapshot() { return read(); }

        private void put(String key, Tag value) {
            validateKey(key);
            edit(tag -> tag.put(key, value.copy()));
        }

        private <T> Optional<T> decode(String key, byte expected, Function<Tag, T> decoder) {
            validateKey(key);
            Tag value = read().get(key);
            if (value == null) return Optional.empty();
            if (value.getId() != expected) throw mismatch(expected, value);
            return Optional.of(decoder.apply(value));
        }

        private static Number number(Tag tag) {
            return tag.asNumber().orElseThrow(() -> mismatch(tag.getId(), tag));
        }

        private static IllegalArgumentException mismatch(byte expected, Tag actual) {
            return new IllegalArgumentException("Persistent data tag type mismatch: expected "
                + expected + ", got " + actual.getId());
        }
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException(
                "World persistent data must be accessed on the Minecraft server thread");
        }
    }

    private static void validateNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isBlank() || !namespace.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid persistent-data namespace: " + namespace);
        }
    }

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) throw new IllegalArgumentException("Persistent data key must not be blank");
        if (key.length() > 128) {
            throw new IllegalArgumentException("Persistent data key is longer than 128 characters");
        }
    }
}
