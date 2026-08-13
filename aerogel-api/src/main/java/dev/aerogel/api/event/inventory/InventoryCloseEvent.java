package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

public record InventoryCloseEvent(ServerPlayer player) implements PlayerEvent {
}
