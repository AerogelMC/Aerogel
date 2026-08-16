package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Fired after vanilla finishes placing a player and before its join announcement is broadcast.
 * Cancelling this event suppresses only the announcement; the player remains connected.
 */
public final class PlayerJoinEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final Connection connection;
    private final int previousPlayerCount;
    private final int updatedPlayerCount;
    private Component message;
    private boolean cancelled;

    /** Creates an event with Minecraft's normal join announcement. */
    public PlayerJoinEvent(ServerPlayer player, Connection connection) {
        this(
            player,
            connection,
            Component.translatable("multiplayer.player.joined", player.getDisplayName())
                .withStyle(ChatFormatting.YELLOW)
        );
    }

    public PlayerJoinEvent(ServerPlayer player, Connection connection, Component message) {
        this.player = Objects.requireNonNull(player, "player");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.message = Objects.requireNonNull(message, "message");
        int updatedCount = player.level().getServer().getPlayerList().getPlayers().size();
        this.previousPlayerCount = Math.max(0, updatedCount - 1);
        this.updatedPlayerCount = updatedCount;
    }

    public PlayerJoinEvent(
        ServerPlayer player,
        Connection connection,
        Component message,
        int previousPlayerCount,
        int updatedPlayerCount
    ) {
        this.player = Objects.requireNonNull(player, "player");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.message = Objects.requireNonNull(message, "message");
        this.previousPlayerCount = requirePlayerCount(previousPlayerCount, "previousPlayerCount");
        this.updatedPlayerCount = requirePlayerCount(updatedPlayerCount, "updatedPlayerCount");
    }

    @Override public ServerPlayer player() { return player; }
    public Connection connection() { return connection; }
    /** Number of online players immediately before this player joined. */
    public int previousPlayerCount() { return previousPlayerCount; }
    /** Number of online players after this player joined, including {@link #player()}. */
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
