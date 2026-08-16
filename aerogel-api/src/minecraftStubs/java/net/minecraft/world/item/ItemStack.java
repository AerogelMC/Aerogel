package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public final class ItemStack {
    public static final Codec<ItemStack> CODEC = null;
    public static final Codec<ItemStack> OPTIONAL_CODEC = null;
    public static final ItemStack EMPTY = null;
    public ItemStack copy() { return null; }
    public boolean isEmpty() { return false; }
    public InteractionResult useOn(UseOnContext context) { return null; }
}
