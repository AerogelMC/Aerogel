package dev.aerogel.loader.internal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class LootOverlayRegistry {
    private static final Map<ResourceKey<LootTable>, LootTable> TABLES = new LinkedHashMap<>();
    private LootOverlayRegistry() { }

    public static synchronized void add(ResourceKey<LootTable> key, LootTable table) {
        if (TABLES.putIfAbsent(key, table) != null) {
            throw new IllegalStateException("A plugin loot table is already registered as " + key.identifier());
        }
    }
    public static synchronized void remove(ResourceKey<LootTable> key, LootTable table) {
        TABLES.remove(key, table);
    }
    public static synchronized Optional<LootTable> find(ResourceKey<LootTable> key) {
        return Optional.ofNullable(TABLES.get(key));
    }
}
