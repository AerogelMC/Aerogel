package net.minecraft.server.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class ServerGamePacketListenerImpl {
    public ServerPlayer player;
    public void send(Packet<? super ClientGamePacketListener> packet) { }
    public void disconnect(Component reason) { }
    public void resetPosition() { }
}
