package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Fired before vanilla changes a player's game mode. */
public final class PlayerGameModeChangeEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final GameType previousGameMode;
    private GameType gameMode;
    private boolean cancelled;

    public PlayerGameModeChangeEvent(ServerPlayer player, GameType gameMode) {
        this(player, player.gameMode(), gameMode);
    }

    public PlayerGameModeChangeEvent(
        ServerPlayer player, GameType previousGameMode, GameType gameMode
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.previousGameMode = Objects.requireNonNull(previousGameMode, "previousGameMode");
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
    }

    @Override public ServerPlayer player() { return player; }
    public GameType previousGameMode() { return previousGameMode; }
    public GameType gameMode() { return gameMode; }
    public void setGameMode(GameType gameMode) {
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
