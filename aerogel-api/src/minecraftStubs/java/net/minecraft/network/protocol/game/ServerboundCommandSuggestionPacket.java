package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundCommandSuggestionPacket implements Packet<Object> {
    public int getId() { return 0; }
    public String getCommand() { return null; }
}
