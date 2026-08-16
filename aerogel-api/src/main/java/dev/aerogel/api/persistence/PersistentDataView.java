package dev.aerogel.api.persistence;

import dev.aerogel.api.PluginContext;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Set;

/** Namespaced persistent data attached directly to a vanilla object. */
public interface PersistentDataView {
    PersistentDataContainer namespace(String namespace);

    default PersistentDataContainer namespace(PluginContext plugin) {
        return namespace(Objects.requireNonNull(plugin, "plugin").pluginId());
    }

    Set<String> namespaces();
    boolean containsNamespace(String namespace);
    void removeNamespace(String namespace);
    CompoundTag snapshot();
}
