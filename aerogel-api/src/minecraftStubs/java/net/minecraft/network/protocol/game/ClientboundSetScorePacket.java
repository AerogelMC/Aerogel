package net.minecraft.network.protocol.game;
public record ClientboundSetScorePacket(String owner, String objectiveName, int score,
    java.util.Optional<net.minecraft.network.chat.Component> display,
    java.util.Optional<net.minecraft.network.chat.numbers.NumberFormat> numberFormat)
    implements net.minecraft.network.protocol.Packet<ClientGamePacketListener> { }
