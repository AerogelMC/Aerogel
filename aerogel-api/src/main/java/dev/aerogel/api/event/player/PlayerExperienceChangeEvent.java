package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

/** Fired before experience points or levels are added or removed. */
public final class PlayerExperienceChangeEvent implements PlayerEvent, CancellableEvent {
    public enum Unit { POINTS, LEVELS }

    private final ServerPlayer player;
    private final int previousTotalExperience;
    private final int previousLevel;
    private final float previousProgress;
    private int amount;
    private final Unit unit;
    private boolean cancelled;

    public PlayerExperienceChangeEvent(ServerPlayer player, int amount, Unit unit) {
        this.player = player;
        previousTotalExperience = player.totalExperience;
        previousLevel = player.experienceLevel;
        previousProgress = player.experienceProgress;
        this.amount = amount;
        this.unit = unit;
    }

    @Override public ServerPlayer player() { return player; }
    public int previousTotalExperience() { return previousTotalExperience; }
    public int previousLevel() { return previousLevel; }
    public float previousProgress() { return previousProgress; }
    public int amount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public Unit unit() { return unit; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
