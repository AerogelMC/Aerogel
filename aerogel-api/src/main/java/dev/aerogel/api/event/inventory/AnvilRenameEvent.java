package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.TypedPlayerPacketEvent;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before an anvil rename request changes the result name. */
public final class AnvilRenameEvent extends TypedPlayerPacketEvent<ServerboundRenameItemPacket> {
    public AnvilRenameEvent(ServerPlayer player, ServerboundRenameItemPacket packet) { super(player, packet); }
}
