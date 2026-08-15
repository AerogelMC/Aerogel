package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Fired before a player's sprinting state changes. */
public final class PlayerSprintChangeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final boolean previous;
    private boolean sprinting;
    private boolean cancelled;

    public PlayerSprintChangeEvent(ServerPlayer player, boolean previous, boolean sprinting) {
        this.player = Objects.requireNonNull(player, "player");
        this.previous = previous;
        this.sprinting = sprinting;
    }

    @Override public ServerPlayer player() { return player; }
    public boolean previous() { return previous; }
    public boolean sprinting() { return sprinting; }
    public void setSprinting(boolean sprinting) { this.sprinting = sprinting; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
