package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

/** Fired before exhaustion is added to a player's food data. */
public final class PlayerFoodExhaustionEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final float amount;
    private boolean cancelled;

    public PlayerFoodExhaustionEvent(ServerPlayer player, float amount) {
        this.player = player;
        this.amount = amount;
    }

    @Override public ServerPlayer player() { return player; }
    public float amount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
