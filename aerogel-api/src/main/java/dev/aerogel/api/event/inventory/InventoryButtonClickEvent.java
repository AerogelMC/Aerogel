package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.TypedPlayerPacketEvent;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a menu-specific button action is handled. */
public final class InventoryButtonClickEvent
    extends TypedPlayerPacketEvent<ServerboundContainerButtonClickPacket> {
    public InventoryButtonClickEvent(ServerPlayer player, ServerboundContainerButtonClickPacket packet) {
        super(player, packet);
    }
}
