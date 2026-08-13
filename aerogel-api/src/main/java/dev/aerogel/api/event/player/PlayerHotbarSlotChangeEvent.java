package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a selected hotbar-slot change is applied. */
public final class PlayerHotbarSlotChangeEvent extends TypedPlayerPacketEvent<ServerboundSetCarriedItemPacket> {
    public PlayerHotbarSlotChangeEvent(ServerPlayer player, ServerboundSetCarriedItemPacket packet) { super(player, packet); }
}
