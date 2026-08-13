package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a server player attempts to enter a bed. */
public final class PlayerBedEnterEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final BlockPos position;
    private boolean cancelled;

    public PlayerBedEnterEvent(ServerPlayer player, BlockPos position) {
        this.player = player;
        this.position = position;
    }

    @Override public ServerPlayer player() { return player; }
    public BlockPos position() { return position; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
