package dev.aerogel.api.item;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Fluent editing of a real vanilla ItemStack and its data components. */
public interface ItemBuilder {
    ItemBuilder amount(int amount);
    ItemBuilder damage(int damage);
    ItemBuilder name(Component name);
    ItemBuilder clearName();
    ItemBuilder lore(List<Component> lines);
    default ItemBuilder lore(Component... lines) { return lore(List.of(lines)); }
    ItemBuilder glint(boolean glint);
    ItemBuilder unbreakable(boolean unbreakable);
    ItemBuilder model(Identifier model);
    <T> ItemBuilder component(DataComponentType<T> type, T value);
    ItemBuilder remove(DataComponentType<?> type);
    /** Returns the live stack being edited. */
    ItemStack stack();
    /** Returns an independent copy of the edited stack. */
    ItemStack build();
}
