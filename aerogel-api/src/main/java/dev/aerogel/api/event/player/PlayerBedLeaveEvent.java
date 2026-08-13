package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a server player leaves a bed. */
public final class PlayerBedLeaveEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final boolean resetSleepTimer;
    private final boolean updateSleepingPlayers;
    private boolean cancelled;

    public PlayerBedLeaveEvent(
        ServerPlayer player, boolean resetSleepTimer, boolean updateSleepingPlayers
    ) {
        this.player = player;
        this.resetSleepTimer = resetSleepTimer;
        this.updateSleepingPlayers = updateSleepingPlayers;
    }

    @Override public ServerPlayer player() { return player; }
    public boolean resetSleepTimer() { return resetSleepTimer; }
    public boolean updateSleepingPlayers() { return updateSleepingPlayers; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
