package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Fired immediately before a player enters vanilla's active item-use state. */
public final class PlayerItemUseStartEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final InteractionHand hand;
    private final ItemStack item;
    private boolean cancelled;

    public PlayerItemUseStartEvent(
        ServerPlayer player, InteractionHand hand, ItemStack item
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.item = Objects.requireNonNull(item, "item");
    }

    @Override public ServerPlayer player() { return player; }
    public InteractionHand hand() { return hand; }
    public ItemStack item() { return item; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
