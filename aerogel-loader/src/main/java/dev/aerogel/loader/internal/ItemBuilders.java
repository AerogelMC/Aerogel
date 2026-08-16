package dev.aerogel.loader.internal;

import dev.aerogel.api.item.ItemBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Objects;

/** Direct fluent editing for vanilla item stacks. */
public final class ItemBuilders {
    private ItemBuilders() { }

    public static ItemBuilder edit(ItemStack stack) {
        return new Builder(Objects.requireNonNull(stack, "stack"));
    }

    private static final class Builder implements ItemBuilder {
        private final ItemStack stack;

        private Builder(ItemStack stack) { this.stack = stack; }
        @Override public ItemBuilder amount(int amount) {
            if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
            stack.setCount(amount); return this;
        }
        @Override public ItemBuilder damage(int damage) {
            if (damage < 0) throw new IllegalArgumentException("damage must not be negative");
            stack.setDamageValue(damage); return this;
        }
        @Override public ItemBuilder name(Component name) {
            stack.set(DataComponents.CUSTOM_NAME, Objects.requireNonNull(name)); return this;
        }
        @Override public ItemBuilder clearName() { stack.remove(DataComponents.CUSTOM_NAME); return this; }
        @Override public ItemBuilder lore(List<Component> lines) {
            stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lines))); return this;
        }
        @Override public ItemBuilder glint(boolean glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint); return this;
        }
        @Override public ItemBuilder unbreakable(boolean unbreakable) {
            if (unbreakable) stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            else stack.remove(DataComponents.UNBREAKABLE);
            return this;
        }
        @Override public ItemBuilder model(Identifier model) {
            stack.set(DataComponents.ITEM_MODEL, Objects.requireNonNull(model)); return this;
        }
        @Override public <T> ItemBuilder component(DataComponentType<T> type, T value) {
            stack.set(Objects.requireNonNull(type), Objects.requireNonNull(value)); return this;
        }
        @Override public ItemBuilder remove(DataComponentType<?> type) {
            stack.remove(Objects.requireNonNull(type)); return this;
        }
        @Override public ItemStack stack() { return stack; }
        @Override public ItemStack build() { return stack.copy(); }
    }
}
