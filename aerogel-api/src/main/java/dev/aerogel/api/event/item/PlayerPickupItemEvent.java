package dev.aerogel.api.event.item;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerPickupItemEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final ItemEntity itemEntity;
    private boolean cancelled;

    public PlayerPickupItemEvent(ServerPlayer player, ItemEntity itemEntity) {
        this.player = player;
        this.itemEntity = itemEntity;
    }

    @Override public ServerPlayer player() { return player; }
    public ItemEntity itemEntity() { return itemEntity; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
