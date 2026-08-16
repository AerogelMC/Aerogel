package net.minecraft.network;

public class Connection {
    public void send(net.minecraft.network.protocol.Packet<?> packet) { }
    public void tick() { }
    public boolean isConnected() { return false; }
    public void disconnect(net.minecraft.network.chat.Component reason) { }
    public java.net.SocketAddress getRemoteAddress() { return null; }
}
