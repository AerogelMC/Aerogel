package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundSeenAdvancementsPacket implements Packet<Object> {
    public enum Action { OPENED_TAB, CLOSED_SCREEN }
    public Action getAction() { return null; }
    public net.minecraft.resources.Identifier getTab() { return null; }
}
