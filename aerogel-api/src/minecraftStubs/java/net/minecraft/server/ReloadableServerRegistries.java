package net.minecraft.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

public final class ReloadableServerRegistries {
    public static class Holder {
        public LootTable getLootTable(ResourceKey<LootTable> key) { return null; }
    }
}
