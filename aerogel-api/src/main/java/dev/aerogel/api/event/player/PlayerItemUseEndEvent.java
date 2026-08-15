package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Fired before an active item use completes, is released, or is interrupted. */
public final class PlayerItemUseEndEvent implements PlayerEvent, CancellableEvent {
    public enum Reason {
        COMPLETED,
        RELEASED,
        INTERRUPTED
    }

    private final ServerPlayer player;
    private final InteractionHand hand;
    private final ItemStack item;
    private final Reason reason;
    private int remainingTicks;
    private boolean cancelled;

    public PlayerItemUseEndEvent(
        ServerPlayer player,
        InteractionHand hand,
        ItemStack item,
        Reason reason,
        int remainingTicks
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.item = Objects.requireNonNull(item, "item");
        this.reason = Objects.requireNonNull(reason, "reason");
        setRemainingTicks(remainingTicks);
    }

    @Override public ServerPlayer player() { return player; }
    public InteractionHand hand() { return hand; }
    public ItemStack item() { return item; }
    public Reason reason() { return reason; }
    public int remainingTicks() { return remainingTicks; }

    /** Changes the charge/use time supplied to release handling. */
    public void setRemainingTicks(int remainingTicks) {
        if (remainingTicks < 0) {
            throw new IllegalArgumentException("remainingTicks must not be negative");
        }
        this.remainingTicks = remainingTicks;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
