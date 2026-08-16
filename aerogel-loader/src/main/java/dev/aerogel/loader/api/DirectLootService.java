package dev.aerogel.loader.api;

import dev.aerogel.api.Registration;
import dev.aerogel.api.loot.LootService;
import dev.aerogel.loader.internal.LootOverlayRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectLootService implements LootService {
    private final PluginApiScope scope;
    DirectLootService(PluginApiScope scope) { this.scope = scope; }

    @Override public Registration register(Identifier id, LootTable table) {
        requireOwned(id);
        ResourceKey<LootTable> key = key(id);
        Objects.requireNonNull(table, "table");
        LootOverlayRegistry.add(key, table);
        return scope.own(new Registration() {
            private final AtomicBoolean active = new AtomicBoolean(true);
            @Override public boolean active() { return active.get(); }
            @Override public void close() {
                if (active.compareAndSet(true, false)) LootOverlayRegistry.remove(key, table);
            }
        });
    }
    @Override public Optional<LootTable> find(Identifier id) {
        ResourceKey<LootTable> key = key(id);
        return LootOverlayRegistry.find(key)
            .or(() -> Optional.ofNullable(scope.vanilla().reloadableRegistries().getLootTable(key)));
    }
    @Override public List<ItemStack> generate(Identifier id, LootParams parameters) {
        return List.copyOf(required(id).getRandomItems(parameters));
    }
    @Override public List<ItemStack> generate(Identifier id, LootParams parameters, long seed) {
        return List.copyOf(required(id).getRandomItems(parameters, seed));
    }
    @Override public void fill(Identifier id, Container destination, LootParams parameters, long seed) {
        required(id).fill(destination, parameters, seed);
    }
    @Override public ResourceKey<LootTable> key(Identifier id) {
        return ResourceKey.create(Registries.LOOT_TABLE, Objects.requireNonNull(id, "id"));
    }
    @Override public String pluginNamespace() { return scope.pluginId(); }
    private LootTable required(Identifier id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown loot table: " + id));
    }
    private void requireOwned(Identifier id) {
        Objects.requireNonNull(id, "id");
        if (!scope.pluginId().equals(id.getNamespace())) {
            throw new IllegalArgumentException("Plugin loot tables must use namespace " + scope.pluginId() + ": " + id);
        }
    }
}
