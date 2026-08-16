package net.minecraft.world.item.component;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

public final class CustomData {
    public static final CustomData EMPTY = null;
    public static CustomData of(CompoundTag tag) { return null; }
    public static void update(DataComponentType<CustomData> type, ItemStack stack,
                              Consumer<CompoundTag> editor) { }
    public CompoundTag copyTag() { return null; }
}
