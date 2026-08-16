package dev.aerogel.loader.internal;

import dev.aerogel.api.persistence.PersistentDataContainer;
import dev.aerogel.api.persistence.PersistentDataType;
import dev.aerogel.api.persistence.PersistentDataView;
import net.minecraft.nbt.CompoundTag;
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
import java.util.function.Consumer;

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

    private static final class EntityContainer implements PersistentDataContainer {
        private final PersistentDataHolderBridge holder;
        private final String namespace;

        private EntityContainer(PersistentDataHolderBridge holder, String namespace) {
            this.holder = holder;
            this.namespace = namespace;
        }

        @Override
        public <T> void set(String key, PersistentDataType<T> type, T value) {
            validateKey(key);
            Objects.requireNonNull(type, "type");
            Tag encoded = Objects.requireNonNull(
                type.encode(Objects.requireNonNull(value, "value")), "encoded value");
            edit(tag -> tag.put(key, encoded.copy()));
        }

        @Override
        public <T> Optional<T> get(String key, PersistentDataType<T> type) {
            validateKey(key);
            Objects.requireNonNull(type, "type");
            Tag value = read().get(key);
            return value == null ? Optional.empty() : Optional.of(type.decode(value.copy()));
        }

        @Override public boolean contains(String key) { validateKey(key); return read().contains(key); }
        @Override public void remove(String key) { validateKey(key); edit(tag -> tag.remove(key)); }
        @Override public Set<String> keys() { return Set.copyOf(read().keySet()); }
        @Override public void clear() { edit(tag -> Set.copyOf(tag.keySet()).forEach(tag::remove)); }
        @Override public CompoundTag snapshot() { return read(); }

        private CompoundTag read() {
            return holder.aerogel$persistentData(namespace);
        }

        private void edit(Consumer<CompoundTag> editor) {
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

        @Override public <T> void set(String key, PersistentDataType<T> type, T value) {
            validateKey(key);
            Objects.requireNonNull(type, "type");
            Tag encoded = Objects.requireNonNull(
                type.encode(Objects.requireNonNull(value, "value")), "encoded value");
            edit(tag -> tag.put(key, encoded.copy()));
        }
        @Override public <T> Optional<T> get(String key, PersistentDataType<T> type) {
            validateKey(key);
            Objects.requireNonNull(type, "type");
            Tag value = read().get(key);
            return value == null ? Optional.empty() : Optional.of(type.decode(value.copy()));
        }
        @Override public boolean contains(String key) { validateKey(key); return read().contains(key); }
        @Override public void remove(String key) { validateKey(key); edit(tag -> tag.remove(key)); }
        @Override public Set<String> keys() { return Set.copyOf(read().keySet()); }
        @Override public void clear() { edit(tag -> Set.copyOf(tag.keySet()).forEach(tag::remove)); }
        @Override public CompoundTag snapshot() { return read(); }
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
