package dev.aerogel.api.event.inventory;

import dev.aerogel.api.event.player.PlayerPacketEvent;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;

public final class InventoryClickEvent extends PlayerPacketEvent {
    private final int containerId;
    private final int stateId;
    private final int slot;
    private final int button;
    private final ContainerInput input;

    public InventoryClickEvent(
        ServerPlayer player, ServerboundContainerClickPacket packet, int containerId, int stateId,
        int slot, int button, ContainerInput input
    ) {
        super(player, packet);
        this.containerId = containerId;
        this.stateId = stateId;
        this.slot = slot;
        this.button = button;
        this.input = input;
    }

    public int containerId() { return containerId; }
    public int stateId() { return stateId; }
    public int slot() { return slot; }
    public int button() { return button; }
    public ContainerInput input() { return input; }
    @Override public ServerboundContainerClickPacket packet() {
        return (ServerboundContainerClickPacket) super.packet();
    }
}
