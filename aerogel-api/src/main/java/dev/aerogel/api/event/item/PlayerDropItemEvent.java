package dev.aerogel.api.event.item;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDropItemEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final ItemStack itemStack;
    private final boolean randomThrow;
    private final boolean retainOwnership;
    private boolean cancelled;

    public PlayerDropItemEvent(
        ServerPlayer player, ItemStack itemStack, boolean randomThrow, boolean retainOwnership
    ) {
        this.player = player;
        this.itemStack = itemStack;
        this.randomThrow = randomThrow;
        this.retainOwnership = retainOwnership;
    }

    @Override public ServerPlayer player() { return player; }
    public ItemStack itemStack() { return itemStack; }
    public boolean randomThrow() { return randomThrow; }
    public boolean retainOwnership() { return retainOwnership; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
