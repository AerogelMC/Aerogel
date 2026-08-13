package dev.aerogel.api.event.player;

import dev.aerogel.api.event.CancellableEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

/** Base for cancellable events raised before a serverbound player packet is handled. */
public abstract class PlayerPacketEvent implements PlayerEvent, CancellableEvent {
    private final ServerPlayer player;
    private final Packet<?> packet;
    private boolean cancelled;

    protected PlayerPacketEvent(ServerPlayer player, Packet<?> packet) {
        this.player = player;
        this.packet = packet;
    }

    @Override public final ServerPlayer player() { return player; }
    public Packet<?> packet() { return packet; }
    @Override public final boolean isCancelled() { return cancelled; }
    @Override public final void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
