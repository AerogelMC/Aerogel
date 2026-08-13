package dev.aerogel.api.event.player;

public final class PlayerActionEvent extends PlayerPacketEvent {
    public PlayerActionEvent(Object playerHandle, Object packetHandle) { super(playerHandle, packetHandle); }
}
