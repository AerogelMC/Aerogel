package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Fired before a player's sneaking state changes. */
public final class PlayerSneakChangeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final boolean previous;
    private boolean sneaking;
    private boolean cancelled;

    public PlayerSneakChangeEvent(ServerPlayer player, boolean previous, boolean sneaking) {
        this.player = Objects.requireNonNull(player, "player");
        this.previous = previous;
        this.sneaking = sneaking;
    }

    @Override public ServerPlayer player() { return player; }
    public boolean previous() { return previous; }
    public boolean sneaking() { return sneaking; }
    public void setSneaking(boolean sneaking) { this.sneaking = sneaking; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
