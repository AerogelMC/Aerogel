package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

public class SimpleContainer implements Container {
    public SimpleContainer(int size) { }
    public int getContainerSize() { return 0; }
    public ItemStack getItem(int slot) { return null; }
    public void setItem(int slot, ItemStack item) { }
    public void clearContent() { }
}
