package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.PlayerPacketEvent;

public final class InventoryClickEvent extends PlayerPacketEvent {
    public InventoryClickEvent(Object playerHandle, Object packetHandle) { super(playerHandle, packetHandle); }
}
