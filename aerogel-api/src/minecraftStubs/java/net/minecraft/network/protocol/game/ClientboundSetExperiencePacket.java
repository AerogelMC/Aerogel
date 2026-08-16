package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public class ClientboundSetExperiencePacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetExperiencePacket(float progress, int totalExperience, int level) { }
}
