package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

import java.util.List;
import java.util.UUID;

public class ClientboundPlayerInfoRemovePacket implements Packet<ClientGamePacketListener> {
    public ClientboundPlayerInfoRemovePacket(List<UUID> profileIds) { }
}
