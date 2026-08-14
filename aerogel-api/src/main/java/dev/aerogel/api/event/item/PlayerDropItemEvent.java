package dev.aerogel.api.event.item;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class PlayerDropItemEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private ItemStack itemStack;
    private boolean randomThrow;
    private boolean retainOwnership;
    private boolean cancelled;

    public PlayerDropItemEvent(
        ServerPlayer player, ItemStack itemStack, boolean randomThrow, boolean retainOwnership
    ) {
        this.player = player;
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack");
        this.randomThrow = randomThrow;
        this.retainOwnership = retainOwnership;
    }

    @Override public ServerPlayer player() { return player; }
    public ItemStack itemStack() { return itemStack; }
    public void setItemStack(ItemStack itemStack) {
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack");
    }
    public boolean randomThrow() { return randomThrow; }
    public void setRandomThrow(boolean randomThrow) { this.randomThrow = randomThrow; }
    public boolean retainOwnership() { return retainOwnership; }
    public void setRetainOwnership(boolean retainOwnership) { this.retainOwnership = retainOwnership; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
