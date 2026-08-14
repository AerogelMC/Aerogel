package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Low-level hand-animation packet event.
 *
 * <p>A swing is not necessarily a click: dropping an item and other actions may also produce
 * this packet. Use {@link PlayerInteractEvent} for gameplay interaction handling.</p>
 */
public final class PlayerSwingEvent extends TypedPlayerPacketEvent<ServerboundSwingPacket> {
    public PlayerSwingEvent(ServerPlayer player, ServerboundSwingPacket packet) { super(player, packet); }
}
