package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public class ClientboundGameEventPacket implements Packet<ClientGamePacketListener> {
    public static final Type START_RAINING = null;
    public static final Type STOP_RAINING = null;
    public static final Type RAIN_LEVEL_CHANGE = null;
    public static final Type THUNDER_LEVEL_CHANGE = null;
    public ClientboundGameEventPacket(Type type, float value) { }
    public static final class Type { }
}
