package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.player.PlayerEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerPlayer;

public final class InventoryOpenEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final MenuProvider provider;
    private boolean cancelled;

    public InventoryOpenEvent(ServerPlayer player, MenuProvider provider) {
        this.player = player;
        this.provider = provider;
    }

    @Override public ServerPlayer player() { return player; }
    public MenuProvider provider() { return provider; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
