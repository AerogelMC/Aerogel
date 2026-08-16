package net.minecraft.world.entity.player;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class Inventory {
    public boolean add(ItemStack stack) { return false; }
    public int clearOrCountMatchingItems(Predicate<ItemStack> filter, int maximum, Container container) {
        return 0;
    }
    public void clearContent() { }
}
