package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Fired immediately before vanilla broadcasts a player's quit announcement.
 * Cancelling this event suppresses only the announcement; disconnection proceeds normally.
 */
public final class PlayerQuitEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final int previousPlayerCount;
    private final int updatedPlayerCount;
    private Component message;
    private boolean cancelled;

    /** Creates an event with Minecraft's normal quit announcement. */
    public PlayerQuitEvent(ServerPlayer player) {
        this(
            player,
            Component.translatable("multiplayer.player.left", player.getDisplayName())
                .withStyle(ChatFormatting.YELLOW)
        );
    }

    public PlayerQuitEvent(ServerPlayer player, Component message) {
        this.player = Objects.requireNonNull(player, "player");
        this.message = Objects.requireNonNull(message, "message");
        int previousCount = player.level().getServer().getPlayerList().getPlayers().size();
        this.previousPlayerCount = previousCount;
        this.updatedPlayerCount = Math.max(0, previousCount - 1);
    }

    public PlayerQuitEvent(
        ServerPlayer player,
        Component message,
        int previousPlayerCount,
        int updatedPlayerCount
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.message = Objects.requireNonNull(message, "message");
        this.previousPlayerCount = requirePlayerCount(previousPlayerCount, "previousPlayerCount");
        this.updatedPlayerCount = requirePlayerCount(updatedPlayerCount, "updatedPlayerCount");
    }

    @Override public ServerPlayer player() { return player; }
    /** Number of online players immediately before this player leaves. */
    public int previousPlayerCount() { return previousPlayerCount; }
    /** Projected online-player count after this player leaves. */
    public int updatedPlayerCount() { return updatedPlayerCount; }
    public Component message() { return message; }
    public void setMessage(Component message) {
        this.message = Objects.requireNonNull(message, "message");
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    private static int requirePlayerCount(int count, String name) {
        if (count < 0) throw new IllegalArgumentException(name + " must not be negative");
        return count;
    }
}
