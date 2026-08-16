package net.minecraft.world.level.storage.loot;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class LootTable {
    public ObjectArrayList<ItemStack> getRandomItems(LootParams parameters) { return null; }
    public ObjectArrayList<ItemStack> getRandomItems(LootParams parameters, long seed) { return null; }
    public void fill(Container destination, LootParams parameters, long seed) { }
}
