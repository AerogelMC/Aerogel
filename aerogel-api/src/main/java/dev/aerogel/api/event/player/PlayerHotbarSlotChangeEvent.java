package dev.aerogel.api.event.player;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/** Fired before a selected hotbar-slot change is applied. */
public final class PlayerHotbarSlotChangeEvent extends TypedPlayerPacketEvent<ServerboundSetCarriedItemPacket> {
    private final int previousSlot;
    private final int newSlot;

    public PlayerHotbarSlotChangeEvent(
        ServerPlayer player, ServerboundSetCarriedItemPacket packet
    ) {
        this(player, packet, player.getInventory().getSelectedSlot(), packet.getSlot());
    }

    public PlayerHotbarSlotChangeEvent(
        ServerPlayer player, ServerboundSetCarriedItemPacket packet,
        int previousSlot, int newSlot
    ) {
        super(player, packet);
        requireHotbarSlot(previousSlot, "previousSlot");
        requireHotbarSlot(newSlot, "newSlot");
        this.previousSlot = previousSlot;
        this.newSlot = newSlot;
    }

    public int previousSlot() { return previousSlot; }
    public int newSlot() { return newSlot; }

    private static void requireHotbarSlot(int slot, String name) {
        if (!Inventory.isHotbarSlot(slot)) {
            throw new IllegalArgumentException(name + " must be a hotbar slot");
        }
    }
}
