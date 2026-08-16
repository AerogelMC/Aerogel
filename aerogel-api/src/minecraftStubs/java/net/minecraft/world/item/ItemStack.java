package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.level.ItemLike;

public final class ItemStack {
    public static final Codec<ItemStack> CODEC = null;
    public static final Codec<ItemStack> OPTIONAL_CODEC = null;
    public static final ItemStack EMPTY = null;
    public ItemStack() { }
    public ItemStack copy() { return null; }
    public ItemStack(ItemLike item) { }
    public ItemStack(ItemLike item, int amount) { }
    public int getCount() { return 0; }
    public void setCount(int amount) { }
    public int getDamageValue() { return 0; }
    public void setDamageValue(int damage) { }
    public <T> T set(DataComponentType<T> type, T value) { return null; }
    public <T> T get(DataComponentType<? extends T> type) { return null; }
    public <T> T remove(DataComponentType<? extends T> type) { return null; }
    public boolean isEmpty() { return false; }
    public InteractionResult useOn(UseOnContext context) { return null; }
}
