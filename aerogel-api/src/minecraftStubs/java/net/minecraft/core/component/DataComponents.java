package net.minecraft.core.component;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class DataComponents {
    public static final DataComponentType<CustomData> CUSTOM_DATA = null;
    public static final DataComponentType<Component> CUSTOM_NAME = null;
    public static final DataComponentType<ItemLore> LORE = null;
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT_OVERRIDE = null;
    public static final DataComponentType<Unit> UNBREAKABLE = null;
    public static final DataComponentType<Identifier> ITEM_MODEL = null;
    private DataComponents() { }
}
