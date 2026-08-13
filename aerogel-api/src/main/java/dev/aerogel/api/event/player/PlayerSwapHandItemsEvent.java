package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a player swaps the main-hand and off-hand items. */
public final class PlayerSwapHandItemsEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final ItemStack mainHandItem;
    private final ItemStack offHandItem;
    private boolean cancelled;

    public PlayerSwapHandItemsEvent(
        ServerPlayer player, ItemStack mainHandItem, ItemStack offHandItem
    ) {
        this.player = player;
        this.mainHandItem = mainHandItem;
        this.offHandItem = offHandItem;
    }

    @Override public ServerPlayer player() { return player; }
    public ItemStack mainHandItem() { return mainHandItem; }
    public ItemStack offHandItem() { return offHandItem; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
