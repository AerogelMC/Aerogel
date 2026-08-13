package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.TypedPlayerPacketEvent;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a creative-mode inventory slot update is validated and applied. */
public final class CreativeInventorySlotEvent extends TypedPlayerPacketEvent<ServerboundSetCreativeModeSlotPacket> {
    public CreativeInventorySlotEvent(ServerPlayer player, ServerboundSetCreativeModeSlotPacket packet) {
        super(player, packet);
    }
}
