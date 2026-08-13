package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.PlayerEvent;

public record InventoryCloseEvent(Object playerHandle) implements PlayerEvent {
}
