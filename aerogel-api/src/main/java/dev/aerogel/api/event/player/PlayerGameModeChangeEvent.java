package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;

/** Fired before vanilla changes a player's game mode. */
public final class PlayerGameModeChangeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final GameType gameMode;
    private boolean cancelled;

    public PlayerGameModeChangeEvent(ServerPlayer player, GameType gameMode) {
        this.player = player;
        this.gameMode = gameMode;
    }

    @Override public ServerPlayer player() { return player; }
    public GameType gameMode() { return gameMode; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
