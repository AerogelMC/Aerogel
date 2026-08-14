package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

/** Fired after signed chat validation and before the message is broadcast and logged. */
public final class PlayerChatEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final PlayerChatMessage signedMessage;
    private Component message;
    private ChatRenderer renderer;
    private boolean modified;
    private boolean cancelled;

    public PlayerChatEvent(ServerPlayer player, PlayerChatMessage signedMessage) {
        this.player = Objects.requireNonNull(player, "player");
        this.signedMessage = Objects.requireNonNull(signedMessage, "signedMessage");
        this.message = signedMessage.decoratedContent();
    }

    @Override public ServerPlayer player() { return player; }
    public PlayerChatMessage signedMessage() { return signedMessage; }
    public Component message() { return message; }
    public void setMessage(Component message) {
        this.message = Objects.requireNonNull(message, "message");
        modified = true;
    }
    public boolean isModified() { return modified; }
    /** Changes the complete chat presentation while retaining the player-chat packet. */
    public void setRenderer(ChatRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }
    public Optional<ChatRenderer> renderer() { return Optional.ofNullable(renderer); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
