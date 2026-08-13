package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.TypedPlayerPacketEvent;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;

/** Fired before a recipe-book placement request changes a crafting grid. */
public final class RecipePlaceEvent extends TypedPlayerPacketEvent<ServerboundPlaceRecipePacket> {
    public RecipePlaceEvent(ServerPlayer player, ServerboundPlaceRecipePacket packet) { super(player, packet); }
}
