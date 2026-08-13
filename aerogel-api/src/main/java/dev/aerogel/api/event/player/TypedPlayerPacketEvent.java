package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

/** Typed base for cancellable events raised before a serverbound packet is handled. */
public abstract class TypedPlayerPacketEvent<P extends Packet<?>> extends PlayerPacketEvent {
    protected TypedPlayerPacketEvent(ServerPlayer player, P packet) {
        super(player, packet);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final P packet() {
        return (P) super.packet();
    }
}
