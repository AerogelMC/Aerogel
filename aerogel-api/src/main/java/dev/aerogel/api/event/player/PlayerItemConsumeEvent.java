package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Fired immediately before a server player finishes consuming or using an item. */
public final class PlayerItemConsumeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final InteractionHand hand;
    private final ItemStack item;
    private boolean cancelled;

    public PlayerItemConsumeEvent(ServerPlayer player, InteractionHand hand, ItemStack item) {
        this.player = player;
        this.hand = hand;
        this.item = item;
    }

    @Override public ServerPlayer player() { return player; }
    public InteractionHand hand() { return hand; }
    public ItemStack item() { return item; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
