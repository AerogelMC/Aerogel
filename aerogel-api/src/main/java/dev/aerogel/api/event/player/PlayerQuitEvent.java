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
    }

    @Override public ServerPlayer player() { return player; }
    public Component message() { return message; }
    public void setMessage(Component message) {
        this.message = Objects.requireNonNull(message, "message");
    }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
