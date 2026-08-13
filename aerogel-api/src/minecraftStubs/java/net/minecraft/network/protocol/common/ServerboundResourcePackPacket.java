package net.minecraft.network.protocol.common;
import net.minecraft.network.protocol.Packet;
public class ServerboundResourcePackPacket implements Packet<Object> {
    public enum Action {
        SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, ACCEPTED, DOWNLOADED,
        INVALID_URL, FAILED_RELOAD, DISCARDED;
        public boolean isTerminal() { return false; }
    }
    public java.util.UUID id() { return null; }
    public Action action() { return null; }
}
