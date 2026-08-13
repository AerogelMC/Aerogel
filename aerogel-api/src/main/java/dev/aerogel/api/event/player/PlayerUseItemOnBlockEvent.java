package dev.aerogel.api.event.player;

public final class PlayerUseItemOnBlockEvent extends PlayerPacketEvent {
    public PlayerUseItemOnBlockEvent(Object playerHandle, Object packetHandle) { super(playerHandle, packetHandle); }
}
