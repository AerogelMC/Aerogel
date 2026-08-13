package dev.aerogel.api.event.player;

public final class PlayerUseItemEvent extends PlayerPacketEvent {
    public PlayerUseItemEvent(Object playerHandle, Object packetHandle) { super(playerHandle, packetHandle); }
}
