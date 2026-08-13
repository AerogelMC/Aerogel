package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before book edits or signing are validated and stored. */
public final class PlayerEditBookEvent extends TypedPlayerPacketEvent<ServerboundEditBookPacket> {
    public PlayerEditBookEvent(ServerPlayer player, ServerboundEditBookPacket packet) { super(player, packet); }
}
