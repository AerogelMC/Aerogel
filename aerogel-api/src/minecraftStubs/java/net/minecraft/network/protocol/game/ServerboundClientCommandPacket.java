package net.minecraft.network.protocol.game;
import net.minecraft.network.protocol.Packet;
public class ServerboundClientCommandPacket implements Packet<Object> {
    public enum Action { PERFORM_RESPAWN, REQUEST_STATS, REQUEST_GAMERULE_VALUES }
    public Action getAction() { return null; }
}
