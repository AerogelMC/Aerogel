package net.minecraft.network.protocol.common;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ClientInformation;
public final class ServerboundClientInformationPacket implements Packet<Object> {
    public ClientInformation information() { return null; }
}
