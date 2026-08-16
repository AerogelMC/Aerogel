package net.minecraft.world;

public interface Container {
    int getContainerSize();
    net.minecraft.world.item.ItemStack getItem(int slot);
    void setItem(int slot, net.minecraft.world.item.ItemStack item);
    void clearContent();
}
