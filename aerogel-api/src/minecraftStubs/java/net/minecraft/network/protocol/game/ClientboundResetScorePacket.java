package net.minecraft.network.protocol.game;
public record ClientboundResetScorePacket(String owner, String objectiveName)
    implements net.minecraft.network.protocol.Packet<ClientGamePacketListener> { }
