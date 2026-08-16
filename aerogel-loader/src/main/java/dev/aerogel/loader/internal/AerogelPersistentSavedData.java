package dev.aerogel.loader.internal;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.LinkedHashSet;
import java.util.Set;

/** Native per-world storage for server, world, and coordinate persistent data. */
public final class AerogelPersistentSavedData extends SavedData {
    private static final Codec<AerogelPersistentSavedData> CODEC = CompoundTag.CODEC.xmap(
        AerogelPersistentSavedData::new,
        AerogelPersistentSavedData::rootSnapshot
    );

    public static final SavedDataType<AerogelPersistentSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("aerogel", "persistent_data"),
        AerogelPersistentSavedData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private CompoundTag root;

    public AerogelPersistentSavedData() {
        this(new CompoundTag());
    }

    private AerogelPersistentSavedData(CompoundTag root) {
        this.root = Objects.requireNonNull(root, "root").copy();
    }

    public synchronized CompoundTag snapshot(String pluginId, String target) {
        return root.getCompoundOrEmpty(pluginId).getCompoundOrEmpty(target).copy();
    }

    public synchronized Set<String> namespaces(String target) {
        Set<String> result = new LinkedHashSet<>();
        for (String namespace : root.keySet()) {
            if (root.getCompoundOrEmpty(namespace).contains(target)) result.add(namespace);
        }
        return Set.copyOf(result);
    }

    public synchronized boolean contains(String namespace, String target) {
        return root.getCompoundOrEmpty(namespace).contains(target);
    }

    public synchronized CompoundTag snapshotTarget(String target) {
        CompoundTag result = new CompoundTag();
        for (String namespace : root.keySet()) {
            CompoundTag value = root.getCompoundOrEmpty(namespace).getCompoundOrEmpty(target);
            if (!value.isEmpty()) result.put(namespace, value.copy());
        }
        return result;
    }

    public synchronized void edit(
        String pluginId, String target, Consumer<CompoundTag> editor
    ) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(editor, "editor");

        CompoundTag plugin = root.getCompoundOrEmpty(pluginId).copy();
        CompoundTag value = plugin.getCompoundOrEmpty(target).copy();
        editor.accept(value);

        if (value.isEmpty()) plugin.remove(target); else plugin.put(target, value);
        if (plugin.isEmpty()) root.remove(pluginId); else root.put(pluginId, plugin);
        setDirty();
    }

    private synchronized CompoundTag rootSnapshot() {
        return root.copy();
    }
}
