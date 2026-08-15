package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Fired before a player's swimming state changes. */
public final class PlayerSwimChangeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final boolean previous;
    private boolean swimming;
    private boolean cancelled;

    public PlayerSwimChangeEvent(ServerPlayer player, boolean previous, boolean swimming) {
        this.player = Objects.requireNonNull(player, "player");
        this.previous = previous;
        this.swimming = swimming;
    }

    @Override public ServerPlayer player() { return player; }
    public boolean previous() { return previous; }
    public boolean swimming() { return swimming; }
    public void setSwimming(boolean swimming) { this.swimming = swimming; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
