package dev.aerogel.loader.internal;

import net.minecraft.nbt.CompoundTag;

import java.util.function.Consumer;

/** Internal bridge implemented directly by vanilla entities through Mixin. */
public interface PersistentDataHolderBridge {
    CompoundTag aerogel$persistentData(String pluginId);
    void aerogel$editPersistentData(String pluginId, Consumer<CompoundTag> editor);
    CompoundTag aerogel$allPersistentData();
    void aerogel$restorePersistentData(CompoundTag data);
}
