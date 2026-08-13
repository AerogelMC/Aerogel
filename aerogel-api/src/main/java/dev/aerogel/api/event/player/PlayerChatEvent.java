package dev.aerogel.api.event.player;

public final class PlayerChatEvent extends PlayerPacketEvent {
    public PlayerChatEvent(Object playerHandle, Object packetHandle) { super(playerHandle, packetHandle); }
}
