package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.TypedPlayerPacketEvent;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a merchant trade selection is applied. */
public final class TradeSelectEvent extends TypedPlayerPacketEvent<ServerboundSelectTradePacket> {
    public TradeSelectEvent(ServerPlayer player, ServerboundSelectTradePacket packet) { super(player, packet); }
}
