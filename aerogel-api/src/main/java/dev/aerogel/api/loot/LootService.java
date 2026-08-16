package dev.aerogel.api.loot;

import dev.aerogel.api.Registration;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;

public interface LootService {
    default Registration register(String path, LootTable table) {
        return register(Identifier.fromNamespaceAndPath(pluginNamespace(), path), table);
    }
    Registration register(Identifier id, LootTable table);
    Optional<LootTable> find(Identifier id);
    List<ItemStack> generate(Identifier id, LootParams parameters);
    List<ItemStack> generate(Identifier id, LootParams parameters, long seed);
    void fill(Identifier id, Container destination, LootParams parameters, long seed);
    ResourceKey<LootTable> key(Identifier id);
    String pluginNamespace();
}
