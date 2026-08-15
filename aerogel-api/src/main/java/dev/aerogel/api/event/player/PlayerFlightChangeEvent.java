package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Fired before a player-controlled flying state change is applied. */
public final class PlayerFlightChangeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final boolean previous;
    private boolean flying;
    private boolean cancelled;

    public PlayerFlightChangeEvent(ServerPlayer player, boolean previous, boolean flying) {
        this.player = Objects.requireNonNull(player, "player");
        this.previous = previous;
        this.flying = flying;
    }

    @Override public ServerPlayer player() { return player; }
    public boolean previous() { return previous; }
    public boolean flying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
