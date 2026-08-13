package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

/** Fired before experience points or levels are added or removed. */
public final class PlayerExperienceChangeEvent implements PlayerEvent, CancellableEvent {
    public enum Unit { POINTS, LEVELS }

    private final ServerPlayer player;
    private final int amount;
    private final Unit unit;
    private boolean cancelled;

    public PlayerExperienceChangeEvent(ServerPlayer player, int amount, Unit unit) {
        this.player = player;
        this.amount = amount;
        this.unit = unit;
    }

    @Override public ServerPlayer player() { return player; }
    public int amount() { return amount; }
    public Unit unit() { return unit; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
