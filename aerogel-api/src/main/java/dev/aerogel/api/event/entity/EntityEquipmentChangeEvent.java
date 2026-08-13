package dev.aerogel.api.event.entity;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Fired immediately before a living entity's equipment slot changes. */
public final class EntityEquipmentChangeEvent implements CancellableEvent {
    private final LivingEntity entity;
    private final EquipmentSlot slot;
    private final ItemStack previousItem;
    private final ItemStack item;
    private boolean cancelled;

    public EntityEquipmentChangeEvent(
        LivingEntity entity, EquipmentSlot slot, ItemStack previousItem, ItemStack item
    ) {
        this.entity = entity;
        this.slot = slot;
        this.previousItem = previousItem;
        this.item = item;
    }

    public LivingEntity entity() { return entity; }
    public EquipmentSlot slot() { return slot; }
    public ItemStack previousItem() { return previousItem; }
    public ItemStack item() { return item; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
