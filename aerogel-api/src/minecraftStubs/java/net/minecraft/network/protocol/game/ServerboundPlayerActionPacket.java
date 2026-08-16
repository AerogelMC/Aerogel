package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public final class ServerboundPlayerActionPacket implements Packet<Object> {
    public enum Action {
        START_DESTROY_BLOCK,
        ABORT_DESTROY_BLOCK,
        STOP_DESTROY_BLOCK,
        DROP_ALL_ITEMS,
        DROP_ITEM,
        RELEASE_USE_ITEM,
        SWAP_ITEM_WITH_OFFHAND
    }
    public net.minecraft.core.BlockPos getPos() { return null; }
    public Action getAction() { return null; }
}
